package mailinator

import mailinator.data.write._
import mailinator.data.read._
import mailinator.db.read.{StoreActorMessageIndexView, StoreActorMessageView}
import mailinator.model.write.DefaultWriteService
import mailinator.http.read.ReadHttp
import mailinator.http.write.WriteHttp
import mailinator.config.Settings

import cats.syntax.all._
import cats.effect._
import cats.effect.unsafe.IORuntime

import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._
import org.http4s.circe._
import org.http4s.client.Client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest._
import matchers.should.Matchers._

import com.comcast.ip4s._

class IntegrationSpec extends AnyFlatSpec {
  implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

  val settings = Settings(
    host = ipv4"0.0.0.0",
    port = port"8080",
    maxPageSize = 3,
    messageStoreCapacity = 10
  )

  def testApi[A](check: Client[IO] => IO[A]): A = {
    val messageViewResource = StoreActorMessageView.make[IO](settings)
    val messageIndexViewResource = StoreActorMessageIndexView.make[IO](settings)
    (messageViewResource, messageIndexViewResource).parTupled
      .use { case (messageView, messageIndexView) =>
        val writeService = new DefaultWriteService[IO](messageView, messageIndexView)
        val readHttp = new ReadHttp(messageView, messageIndexView, settings)
        val writeHttp = new WriteHttp(writeService)
        val httpApp: HttpApp[IO] = (readHttp.routes <+> writeHttp.routes).orNotFound
        val client: Client[IO] = Client.fromHttpApp(httpApp)
        check(client)
      }
      .unsafeRunSync()
  }

  "Create mailbox" should "return no content and do nothing" in {
    val (createStatus, getStatus) = testApi { client =>
      for {
        createResult <- client.status(
          Request[IO](method = Method.POST, uri = uri"/mailboxes")
            .withEntity(CreateMailboxRequest(address = "foo@bar.baz").asJson)
        )
        getResult <- client.status(Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages"))
      } yield (createResult, getResult)
    }
    createStatus shouldEqual NoContent
    getStatus shouldEqual NotFound
  }

  val message1 = CreateMessageRequest("sender@sender.com", "subject 1", "body 1")
  val message2 = CreateMessageRequest("sender@sender.com", "subject 2", "body 2")
  val message3 = CreateMessageRequest("sender@sender.com", "subject 3", "body 3")
  val message4 = CreateMessageRequest("sender@sender.com", "subject 4", "body 4")
  val message5 = CreateMessageRequest("sender@sender.com", "subject 5", "body 5")
  val message6 = CreateMessageRequest("sender@sender.com", "subject 6", "body 6")
  val messages = Seq(message1, message2, message3, message4, message5, message6)

  "Create message" should "persist messages so they can be returned both individually and as message index list" in {
    val (createResults, getResults, indexResult) = testApi { client =>
      for {
        // create all messages
        createdJson <- messages
          .map(msg =>
            client.expect[Json](
              Request[IO](method = Method.POST, uri = uri"/mailboxes/foo@bar.baz/messages").withEntity(msg.asJson)
            )
          )
          .sequence
        created <- createdJson.map(_.as[CreateMessageResponse].liftTo[IO]).sequence

        // query all messages one by one
        queriedJson <- created
          .map(msg =>
            client.expect[Json](
              Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages" / msg.id.show)
            )
          )
          .sequence
        queried <- queriedJson.map(_.as[ReadMessageResponse].liftTo[IO]).sequence

        // query the first page of the message index of this mailbox
        indexJson <- client.expect[Json](
          Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages")
        )
        index <- indexJson.as[ReadMessageIndexResponse].liftTo[IO]
      } yield (created, queried, index)
    }

    // individual querying should return all created messages
    getResults.map(_.id) should contain theSameElementsAs createResults.map(_.id)

    // the first page of the message index should be sorted reverse chrono and then by id, and have the correct size
    val expectedIndexList = createResults.sortBy(r => (-r.createdAt, r.id.show))
    indexResult.messages.map(_.id) should contain theSameElementsInOrderAs expectedIndexList.take(3).map(_.id)
    indexResult.nextId shouldBe Option(expectedIndexList.drop(settings.maxPageSize).head.id)
    indexResult.nextCursor shouldBe Option(expectedIndexList.drop(settings.maxPageSize).head.receivedAt)
  }

  "Get message index" should "paginate results correctly" in {
    val (expected, result) = testApi { client =>
      for {
        // create some messages to foo@bar.baz, and some to hello@world.com
        createdJson <- messages.zipWithIndex.map { case (msg, i) =>
          client.expect[Json](
            Request[IO](
              method = Method.POST,
              uri = uri"/mailboxes" / (if (i % 2 == 1) "foo@bar.baz" else "hello@world.com") / "messages"
            ).withEntity(msg.asJson)
          )
        }.sequence
        created <- createdJson.map(_.as[CreateMessageResponse].liftTo[IO]).sequence

        // construct the expected page by filtering and sorting the creation results
        fooPageFromSecond = created.filter(_.recipient == "foo@bar.baz").sortBy(r => (-r.receivedAt, r.id.show)).drop(1)

        // query the first page of the message index of this mailbox from the second item
        indexJson <- client.expect[Json](
          Request[IO](
            method = Method.GET,
            uri = uri"/mailboxes/foo@bar.baz/messages"
              .withQueryParam("timestamp", fooPageFromSecond.head.receivedAt)
              .withQueryParam("id", fooPageFromSecond.head.id.show)
          )
        )
        index <- indexJson.as[ReadMessageIndexResponse].liftTo[IO]
      } yield (fooPageFromSecond, index)
    }

    result.messages.map(_.id) should contain theSameElementsInOrderAs expected.map(_.id)
    result.nextId shouldBe Option.empty
    result.nextCursor shouldBe Option.empty
  }

  // TODO more tests to cover all edge-cases of pagination

  "Delete message" should "delete a message and not return it anymore" in {
    val (createResult2, queryAfterFirstDelete, indexAfterFirstDelete, queryAfterSecondDelete, indexAfterSecondDelete) =
      testApi { client =>
        for {
          // create two messages
          createdJson1 <- client.expect[Json](
            Request[IO](method = Method.POST, uri = uri"/mailboxes/foo@bar.baz/messages").withEntity(message1.asJson)
          )
          created1 <- createdJson1.as[CreateMessageResponse].liftTo[IO]
          createdJson2 <- client.expect[Json](
            Request[IO](method = Method.POST, uri = uri"/mailboxes/foo@bar.baz/messages").withEntity(message2.asJson)
          )
          created2 <- createdJson2.as[CreateMessageResponse].liftTo[IO]

          // delete one message and query the message and the index
          _ <- client.expect[Json](
            Request[IO](method = Method.DELETE, uri = uri"/mailboxes/foo@bar.baz/messages" / created1.id.show)
          )
          queried1 <- client.status(
            Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages" / created1.id.show)
          )
          indexJson1 <- client.expect[Json](
            Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages")
          )
          index1 <- indexJson1.as[ReadMessageIndexResponse].liftTo[IO]

          // delete the last message and query the second message and the index again
          _ <- client.expect[Json](
            Request[IO](method = Method.DELETE, uri = uri"/mailboxes/foo@bar.baz/messages" / created2.id.show)
          )
          queried2 <- client.status(
            Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages" / created2.id.show)
          )
          status2 <- client.status(
            Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages")
          )
        } yield (created2, queried1, index1, queried2, status2)
      }

    queryAfterFirstDelete shouldEqual NotFound
    indexAfterFirstDelete.messages.map(_.id) should contain theSameElementsAs Seq(createResult2.id)
    queryAfterSecondDelete shouldEqual NotFound

    // this actually asserts if the removal of the last message automatically deletes the mailbox too
    indexAfterSecondDelete shouldEqual NotFound
  }

  "Delete mailbox" should "delete all messages of an address" in {
    val (createResults, deleteResult, indexResult) = testApi { client =>
      for {
        // create all messages
        createdJson <- messages
          .map(msg =>
            client.expect[Json](
              Request[IO](method = Method.POST, uri = uri"/mailboxes/foo@bar.baz/messages").withEntity(msg.asJson)
            )
          )
          .sequence
        created <- createdJson.map(_.as[CreateMessageResponse].liftTo[IO]).sequence

        // delete the mailbox
        deletedJson <- client.expect[Json](Request[IO](method = Method.DELETE, uri = uri"/mailboxes/foo@bar.baz"))
        deleted <- deletedJson.as[DeleteMailboxResponse].liftTo[IO]

        // query the index
        index <- client.status(
          Request[IO](method = Method.GET, uri = uri"/mailboxes/foo@bar.baz/messages")
        )
      } yield (created, deleted, index)
    }

    deleteResult.messageCount shouldEqual createResults.size
    indexResult shouldEqual NotFound
  }

  // TODO tests for validation of requests, email addresses, uuids etc.
}

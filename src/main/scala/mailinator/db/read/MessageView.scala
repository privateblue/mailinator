package mailinator.db.read

import cats.syntax.all._
import akka.actor.typed.ActorSystem
import akka.util.Timeout
import cats.effect.Async
import cats.effect.kernel.Resource
import mailinator.data.shared.{MessageId, MessageViewRecord}
import store.StoreActor
import akka.actor.typed.scaladsl.AskPattern._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait MessageView[F[_]] {
  def appendMessage(
      messageId: MessageId,
      receivedAt: Long,
      recipient: String,
      sender: String,
      subject: String,
      body: String
  ): F[MessageViewRecord]

  def removeMailbox(address: String): F[Seq[MessageViewRecord]]

  def removeMessage(address: String, messageId: MessageId): F[MessageViewRecord]

  def retrieveMessage(address: String, messageId: MessageId): F[MessageViewRecord]

  def close(): F[Unit]
}

class StoreActorMessageView[F[_]: Async] extends MessageView[F] {
  private val store = new StoreActor {
    override type Value = MessageViewRecord
    override type PK = String
    override type SK = Long
    override type FK = String
    override val primaryKey = _.messageId
    override val sortKey = _.receivedAt
    override val filterKey = _.recipient
    override implicit val primaryKeyOrdering = Ordering.String
    override implicit val sortKeyOrdering = Ordering.Long.reverse
    override implicit val filterKeyOrdering = Ordering.String
  }

  private implicit val system = ActorSystem(store(), "MessageIndexView")
  private implicit val ec = system.executionContext
  private implicit val timeout: Timeout = 3.seconds

  def close(): F[Unit] =
    for {
      _ <- Async[F].delay { system.terminate() }
      terminated = Async[F].pure(system.whenTerminated)
      _ <- Async[F].fromFuture(terminated)
    } yield ()

  override def appendMessage(
      messageId: MessageId,
      receivedAt: Long,
      recipient: String,
      sender: String,
      subject: String,
      body: String
  ): F[MessageViewRecord] = {
    val record = MessageViewRecord(
      messageId = messageId.show,
      receivedAt = receivedAt,
      recipient = recipient,
      sender = sender,
      subject = subject,
      body = body
    )
    Async[F].fromFuture(
      Async[F].delay(
        system.ask(ref => store.Append(ref, record)).transformWith {
          case Success(value) if value.isEmpty =>
            Future.failed(new IllegalArgumentException(s"Message with id ${messageId.show} already exists"))
          case Success(value) =>
            Future.successful(value.head)
          case Failure(e) =>
            Future.failed(e)
        }
      )
    )
  }

  override def removeMailbox(address: String): F[Seq[MessageViewRecord]] = ???

  override def removeMessage(address: String, messageId: MessageId): F[MessageViewRecord] =
    Async[F].fromFuture(
      Async[F].delay(
        system.ask(ref => store.Remove(ref, messageId.show)).transformWith {
          case Success(records) if records.isEmpty =>
            Future.failed(new IllegalArgumentException(s"Message with id ${messageId.show} cannot be found"))
          // this below is a weird case to check, but the alternative is to check the consistency between the
          // requested address and messageId upfront, which means an extra  db query for every query. while
          // this way we only need extra inserts for edge cases
          case Success(records) if !records.exists(_.recipient == address) =>
            records
              .map(r => system.ask(ref => store.Append(ref, r))) // add deleted records back
              .sequence_
              .flatMap(_ =>
                Future.failed(new IllegalArgumentException(s"Message with id ${messageId.show} cannot be found"))
              )
          case Success(records) =>
            Future.successful(records.find(_.recipient == address).get) // we know it exists, so we can .get here
          case Failure(e) =>
            Future.failed(e)
        }
      )
    )

  override def retrieveMessage(address: String, messageId: MessageId): F[MessageViewRecord] =
    Async[F].fromFuture(
      Async[F].delay(
        system.ask(ref => store.Retrieve(ref, messageId.show)).transformWith {
          case Success(records) if records.isEmpty || !records.exists(_.recipient == address) =>
            Future.failed(new IllegalArgumentException(s"Message with id ${messageId.show} cannot be found"))
          case Success(records) =>
            Future.successful(records.find(_.recipient == address).get) // we know it exists, so we can .get here
          case Failure(e) =>
            Future.failed(e)
        }
      )
    )
}

object StoreActorMessageView {
  def make[F[_]: Async]: Resource[F, MessageView[F]] =
    Resource.make {
      Async[F].delay(new StoreActorMessageView)
    } { view =>
      view.close()
    }
}

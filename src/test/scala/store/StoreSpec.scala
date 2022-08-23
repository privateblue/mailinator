package store

import mailinator.data.shared._

import org.scalatest.flatspec._

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef

class StoreSpec extends ScalaTestWithActorTestKit with AnyFlatSpecLike {
  implicit val ec = system.executionContext

  val index = new StoreActor {
    override type Value = MessageIndexViewRecord
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

  // e, 3, recipient1
  // c, 2, recipient1
  // a, 1, recipient1
  // d, 3, recipient2
  // f, 3, recipient2
  // b, 2, recipient2

  val recordA = MessageIndexViewRecord(
    messageId = "a",
    receivedAt = 1L,
    recipient = "recipient1",
    sender = "sender",
    subject = "test record 1"
  )

  val recordB = MessageIndexViewRecord(
    messageId = "b",
    receivedAt = 2L,
    recipient = "recipient2",
    sender = "sender",
    subject = "test record 2"
  )

  val recordC = MessageIndexViewRecord(
    messageId = "c",
    receivedAt = 2L,
    recipient = "recipient1",
    sender = "sender",
    subject = "test record 3"
  )

  val recordD = MessageIndexViewRecord(
    messageId = "d",
    receivedAt = 3L,
    recipient = "recipient2",
    sender = "sender",
    subject = "test record 4"
  )

  val recordE = MessageIndexViewRecord(
    messageId = "e",
    receivedAt = 3L,
    recipient = "recipient1",
    sender = "sender",
    subject = "test record 5"
  )

  val recordF = MessageIndexViewRecord(
    messageId = "f",
    receivedAt = 3L,
    recipient = "recipient2",
    sender = "sender",
    subject = "test record 6"
  )

  def insert(store: ActorRef[index.Protocol]) =
    for {
      _ <- store.ask(ref => index.Append(ref, recordA))
      _ <- store.ask(ref => index.Append(ref, recordB))
      _ <- store.ask(ref => index.Append(ref, recordC))
      _ <- store.ask(ref => index.Append(ref, recordD))
      _ <- store.ask(ref => index.Append(ref, recordE))
      _ <- store.ask(ref => index.Append(ref, recordF))
    } yield ()

  "RetrieveRange" should "respect the filter and return a cursored page sorted correctly" in {
    val store = spawn(index())
    val result = for {
      _ <- insert(store)
      cursor = Option((recordC.receivedAt, recordC.messageId))
      range1 <- store.ask(ref => index.RetrieveRange(ref, "recipient1", cursor, 3))
      range2 <- store.ask(ref => index.RetrieveRange(ref, "recipient2", cursor, 3))
    } yield (range1, range2)
    result.futureValue._1 should contain theSameElementsInOrderAs Seq(recordC, recordA)
    result.futureValue._2 should contain theSameElementsInOrderAs Seq()
  }
}

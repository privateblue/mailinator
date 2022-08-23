package store

import mailinator.data.read.MessageIndexViewRecord

import org.scalatest.flatspec._

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef

import java.util.UUID

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

  def insertRecords(store: ActorRef[index.Protocol]) =
    for {
      _ <- store.ask(ref => index.Append(ref, recordA))
      _ <- store.ask(ref => index.Append(ref, recordB))
      _ <- store.ask(ref => index.Append(ref, recordC))
      _ <- store.ask(ref => index.Append(ref, recordD))
      _ <- store.ask(ref => index.Append(ref, recordE))
      _ <- store.ask(ref => index.Append(ref, recordF))
    } yield ()

  // the sorted map looks like:
  // e, 3, recipient1
  // c, 2, recipient1
  // a, 1, recipient1
  // d, 3, recipient2
  // f, 3, recipient2
  // b, 2, recipient2

  "RetrieveRange" should "first address, own cursor, limit after end of own records" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option((recordC.receivedAt, recordC.messageId))
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient1", cursor, Option(3)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq(recordC, recordA)
  }

  "RetrieveRange" should "second address, but other recipient's cursor" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option((recordC.receivedAt, recordC.messageId))
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient2", cursor, Option(3)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq()
  }

  "RetrieveRange" should "second address, own cursor, limit at end of own records" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option((recordD.receivedAt, recordD.messageId))
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient2", cursor, Option(3)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq(recordD, recordF, recordB)
  }

  "RetrieveRange" should "first address, but other recipient's cursor" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option((recordD.receivedAt, recordD.messageId))
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient1", cursor, Option(2)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq()
  }

  "RetrieveRange" should "first address, no cursor, limit before the end of own records" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option.empty
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient1", cursor, Option(3)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq(recordE, recordC, recordA)
  }

  "RetrieveRange" should "first address, no cursor, limit after the end of own records" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option.empty
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient1", cursor, Option(4)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq(recordE, recordC, recordA)
  }

  "RetrieveRange" should "second address, no cursor, limit before the end of own records" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option.empty
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient2", cursor, Option(3)))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq(recordD, recordF, recordB)
  }

  "RetrieveRange" should "second address, no cursor, no limit" in {
    val store = spawn(index(), UUID.randomUUID().toString)
    val result = for {
      _ <- insertRecords(store)
      cursor = Option.empty
      range <- store.ask(ref => index.RetrieveRange(ref, "recipient2", cursor, Option.empty))
    } yield range
    result.futureValue should contain theSameElementsInOrderAs Seq(recordD, recordF, recordB)
  }
}

package mailinator.db.read

import mailinator.data.shared.MessageId
import mailinator.data.read.MessageViewRecord

import store.StoreActor
import cats.syntax.all._
import cats.effect.Async
import cats.effect.kernel.Resource

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorSystem
import akka.util.Timeout

import java.util.MissingResourceException

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

  def removeMessage(messageId: MessageId): F[MessageViewRecord]

  def retrieveMessage(messageId: MessageId): F[MessageViewRecord]

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

  override def removeMailbox(address: String): F[Seq[MessageViewRecord]] =
    Async[F].fromFuture(
      Async[F].delay(
        for {
          records <- system.ask(ref => store.RetrieveRange(ref, address, Option.empty, Option.empty))
          deleted <- records
            .map(r => system.ask(ref => store.Remove(ref, r.messageId)))
            .sequence
            .map(_.flatten)
            .transformWith {
              case Success(rs) if rs.isEmpty =>
                Future.failed(new MissingResourceException(s"Mailbox $address cannot be found", "Mailbox", address))
              case Success(rs) =>
                Future.successful(rs)
              case Failure(e) =>
                Future.failed(e)
            }
        } yield deleted
      )
    )

  override def removeMessage(messageId: MessageId): F[MessageViewRecord] =
    Async[F].fromFuture(
      Async[F].delay(
        system.ask(ref => store.Remove(ref, messageId.show)).transformWith {
          case Success(records) if records.isEmpty =>
            Future.failed(
              new MissingResourceException(
                s"Message with id ${messageId.show} cannot be found",
                "Message",
                messageId.show
              )
            )
          case Success(records) =>
            Future.successful(records.head)
          case Failure(e) =>
            Future.failed(e)
        }
      )
    )

  override def retrieveMessage(messageId: MessageId): F[MessageViewRecord] =
    Async[F].fromFuture(
      Async[F].delay(
        system.ask(ref => store.Retrieve(ref, messageId.show)).transformWith {
          case Success(records) if records.isEmpty =>
            Future.failed(
              new MissingResourceException(
                s"Message with id ${messageId.show} cannot be found",
                "Message",
                messageId.show
              )
            )
          case Success(records) =>
            Future.successful(records.head)
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

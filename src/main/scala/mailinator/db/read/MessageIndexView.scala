package mailinator.db.read

import mailinator.data.read.{MessageIndexViewRecord, Page}
import mailinator.data.shared.MessageId
import mailinator.config.Settings

import store.StoreActor

import cats.syntax.all._
import cats.effect.Async
import cats.effect.kernel.Resource

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import java.util.MissingResourceException

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait MessageIndexView[F[_]] {
  def appendMessage(
      messageId: MessageId,
      receivedAt: Long,
      recipient: String,
      sender: String,
      subject: String
  ): F[MessageIndexViewRecord]

  def removeMailbox(address: String): F[Seq[MessageIndexViewRecord]]

  def removeMessage(messageId: MessageId): F[MessageIndexViewRecord]

  def retrieveMessages(
      address: String,
      from: Option[(Long, MessageId)],
      limit: Int
  ): F[Page[MessageIndexViewRecord, (Long, String)]]

  def close(): F[Unit]
}

class StoreActorMessageIndexView[F[_]: Async](settings: Settings) extends MessageIndexView[F] {
  private val store = new StoreActor {
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

  private implicit val system = ActorSystem(store(settings.messageStoreCapacity), "MessageIndexView")
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
      subject: String
  ): F[MessageIndexViewRecord] = {
    val record = MessageIndexViewRecord(
      messageId = messageId.show,
      receivedAt = receivedAt,
      recipient = recipient,
      sender = sender,
      subject = subject
    )
    Async[F].fromFuture(
      Async[F].delay(
        system.ask(ref => store.Append(ref, record)).transformWith {
          case Success(records) if records.isEmpty =>
            Future.failed(new IllegalArgumentException(s"Message with id ${messageId.show} already exists"))
          case Success(records) =>
            Future.successful(records.head)
          case Failure(e) =>
            Future.failed(e)
        }
      )
    )
  }

  override def removeMailbox(address: String): F[Seq[MessageIndexViewRecord]] =
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

  override def removeMessage(messageId: MessageId): F[MessageIndexViewRecord] =
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

  override def retrieveMessages(
      address: String,
      from: Option[(Long, MessageId)],
      limit: Int
  ): F[Page[MessageIndexViewRecord, (Long, String)]] =
    Async[F].fromFuture(
      Async[F].delay(
        system
          .ask(ref => store.RetrieveRange(ref, address, from.map(f => (f._1, f._2.show)), Option(limit + 1)))
          .transformWith {
            case Success(records) if records.isEmpty =>
              Future.failed(new MissingResourceException(s"Messages of $address cannot be found", "Message", ""))
            case Success(records) if records.size >= limit + 1 =>
              val next = records.last
              Future.successful(Page(records.init, Option((next.receivedAt, next.messageId))))
            case Success(records) =>
              Future.successful(Page(records, Option.empty))
            case Failure(e) =>
              Future.failed(e)
          }
      )
    )
}

object StoreActorMessageIndexView {
  def make[F[_]: Async](settings: Settings): Resource[F, MessageIndexView[F]] =
    Resource.make {
      Async[F].delay(new StoreActorMessageIndexView(settings))
    } { view =>
      view.close()
    }
}

package mailinator.http.write

import mailinator.data.shared.{MessageId, RequestId}
import mailinator.data.write._
import mailinator.model.write.WriteService

import cats.syntax.all._
import cats.effect._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._

class WriteHttp[F[_]: Async](writeService: WriteService[F]) extends Http4sDsl[F] {
  val routes =
    writeCreateMailboxRoute <+> writeCreateMessageRoute <+> writeDeleteMailboxRoute <+> writeDeleteMessageRoute

  implicit val createMailboxRequestDecoder = jsonOf[F, CreateMailboxRequest]
  implicit val createMessageRequestDecoder = jsonOf[F, CreateMessageRequest]

  // POST /mailboxes
  def writeCreateMailboxRoute =
    HttpRoutes.of[F] { case req @ POST -> Root / "mailboxes" =>
      val result = for {
        request <- req.as[CreateMailboxRequest]
        now <- Async[F].realTimeInstant
        requestId <- RequestId.generated[F]
        command <- CreateMailboxCommand.from(request, now.toEpochMilli, requestId)
        _ <- writeService.createMailbox(command)
        status <- Ok()
      } yield status
      result.handleErrorWith(errorHandler)
    }

  // POST /mailboxes/{email address}/messages
  def writeCreateMessageRoute =
    HttpRoutes.of[F] { case req @ POST -> Root / "mailboxes" / address / "messages" =>
      val result = for {
        request <- req.as[CreateMessageRequest]
        messageId <- MessageId.generated[F]
        now <- Async[F].realTimeInstant
        requestId <- RequestId.generated[F]
        command <- CreateMessageCommand.from(request, address, messageId, now.toEpochMilli, requestId)
        event <- writeService.createMessage(command)
        response <- CreateMessageResponse.from(event)
        status <- Ok(response.asJson)
      } yield status
      result.handleErrorWith(errorHandler)
    }

  // DELETE /mailboxes/{email address}
  def writeDeleteMailboxRoute =
    HttpRoutes.of[F] { case DELETE -> Root / "mailboxes" / address =>
      val result = for {
        now <- Async[F].realTimeInstant
        requestId <- RequestId.generated[F]
        command <- DeleteMailboxCommand.from(address, now.toEpochMilli, requestId)
        _ <- writeService.deleteMailbox(command)
        status <- Ok()
      } yield status
      result.handleErrorWith(errorHandler)
    }

  // DELETE /mailboxes/{email address}/messages/{message id}
  def writeDeleteMessageRoute =
    HttpRoutes.of[F] { case DELETE -> Root / "mailboxes" / address / "messages" / id =>
      val result = for {
        messageId <- MessageId.from(id)
        now <- Async[F].realTimeInstant
        requestId <- RequestId.generated[F]
        command <- DeleteMessageCommand.from(address, messageId, now.toEpochMilli, requestId)
        event <- writeService.deleteMessage(command)
        response <- DeleteMessageResponse.from(event)
        status <- Ok(response.asJson)
      } yield status
      result.handleErrorWith(errorHandler)
    }

  def errorHandler(t: Throwable): F[Response[F]] =
    t match {
      // TODO add more failure modes and corresponding http statuses
      case e: IllegalArgumentException => BadRequest(e.getMessage)
      case _                           => InternalServerError()
    }
}

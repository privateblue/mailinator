package mailinator.http.read

import mailinator.data.shared.validation.validateEmailAddress
import mailinator.data.shared.MessageId
import mailinator.data.read._
import mailinator.db.read.{MessageIndexView, MessageView}

import cats.syntax.all._
import cats.effect._

import io.circe.generic.auto._
import io.circe.syntax._

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._

class ReadHttp[F[_]: Async](messageView: MessageView[F], messageIndexView: MessageIndexView[F]) extends Http4sDsl[F] {
  val routes = readMessageIndexRoute <+> readMessageRoute

  object FromTimestampParam extends OptionalQueryParamDecoderMatcher[Long]("timestamp")
  object FromIdParam extends OptionalQueryParamDecoderMatcher[String]("id")

  // GET /mailboxes/{email address}/messages
  def readMessageIndexRoute =
    HttpRoutes.of[F] {
      case GET -> Root / "mailboxes" / address / "messages" :?
          FromTimestampParam(maybeFromTimestamp) +& FromIdParam(maybeFromId) =>
        val result = for {
          _ <- Async[F].fromValidated(validateEmailAddress(address))
          fromId <- maybeFromId.map(fi => MessageId.from(fi)).sequence
          page <- messageIndexView.retrieveMessages(
            address,
            maybeFromTimestamp.flatMap(ft => fromId.map((ft, _))),
            Page.defaultSize
          )
          response <- ReadMessageIndexResponse.from(page)
          status <- Ok(response.asJson)
        } yield status
        result.handleErrorWith(errorHandler)
    }

  // GET /mailboxes/{email address}/messages/{message id}
  def readMessageRoute =
    HttpRoutes.of[F] { case GET -> Root / "mailboxes" / address / "messages" / id =>
      val result = for {
        _ <- Async[F].fromValidated(validateEmailAddress(address))
        messageId <- MessageId.from(id)
        record <- messageView.retrieveMessage(address, messageId)
        response <- ReadMessageResponse.from(record)
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

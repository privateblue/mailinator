package mailinator.data.shared

import validation._

import cats._

import java.util.UUID

case class MessageId(uuid: UUID)

object MessageId {
  def from[F[_]](str: String)(implicit me: MonadError[F, Throwable]): F[MessageId] =
    me.fromValidated(validateUUID(str).map(MessageId.apply))

  def generated[F[_]: Monad]: F[MessageId] =
    Monad[F].pure(MessageId(UUID.randomUUID()))

  implicit val messageIdShow: Show[MessageId] = Show.show(_.uuid.toString)
}

case class RequestId(uuid: UUID)

object RequestId {
  def from[F[_]](str: String)(implicit me: MonadError[F, Throwable]): F[RequestId] =
    me.fromValidated(validateUUID(str).map(RequestId.apply))

  def generated[F[_]: Monad]: F[RequestId] =
    Monad[F].pure(RequestId(UUID.randomUUID()))

  implicit val requestIdShow: Show[RequestId] = Show.show(_.uuid.toString)
}

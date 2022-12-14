package mailinator.data.write

import mailinator.data.shared.MessageId
import mailinator.data.shared.validation.validateEmailAddress

import cats._
import cats.syntax.all._

case class CreateMailboxRequest(
    address: String
) {
  def validated[F[_]](implicit me: MonadError[F, Throwable]): F[CreateMailboxRequest] =
    me.fromValidated(validateEmailAddress(address)).map(_ => this)
}

case class CreateMessageRequest(
    sender: String,
    subject: String,
    body: String
) {
  def validated[F[_]](implicit me: MonadError[F, Throwable]): F[CreateMessageRequest] =
    me.fromValidated(validateEmailAddress(sender)).map(_ => this)
}

case class CreateMessageResponse(
    id: MessageId,
    receivedAt: Long,
    recipient: String,
    sender: String,
    subject: String,
    body: String,
    createdAt: Long
)

object CreateMessageResponse {
  def from[F[_]: Monad](event: MessageCreatedEvent): F[CreateMessageResponse] =
    Monad[F].pure(
      CreateMessageResponse(
        id = event.messageId,
        receivedAt = event.messageReceivedAt,
        recipient = event.address,
        sender = event.sender,
        subject = event.subject,
        body = event.body,
        createdAt = event.messageCreatedAt
      )
    )
}

case class DeleteMailboxResponse(
    address: String,
    deletedAt: Long,
    messageCount: Int
)

object DeleteMailboxResponse {
  def from[F[_]: Monad](event: MailboxDeletedEvent): F[DeleteMailboxResponse] =
    Monad[F].pure(
      DeleteMailboxResponse(
        address = event.address,
        deletedAt = event.mailboxDeletedAt,
        messageCount = event.deletedMessageCount
      )
    )
}

case class DeleteMessageResponse(
    messageId: MessageId,
    receivedAt: Long,
    address: String,
    sender: String,
    subject: String,
    body: String,
    deletedAt: Long
)

object DeleteMessageResponse {
  def from[F[_]: Monad](event: MessageDeletedEvent): F[DeleteMessageResponse] =
    Monad[F].pure(
      DeleteMessageResponse(
        messageId = event.messageId,
        receivedAt = event.messageReceivedAt,
        address = event.address,
        sender = event.sender,
        subject = event.subject,
        body = event.body,
        deletedAt = event.messageDeletedAt
      )
    )
}

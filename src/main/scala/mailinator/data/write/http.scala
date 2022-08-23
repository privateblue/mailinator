package mailinator.data.write

import mailinator.data.shared.MessageId

import cats._

case class CreateMailboxRequest(
    address: String
)

case class CreateMessageRequest(
    sender: String,
    subject: String,
    body: String
)

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

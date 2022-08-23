package mailinator.data.write

import mailinator.data.shared.{MessageId, RequestId}
import mailinator.data.shared.validation.validateEmailAddress

import cats._
import cats.syntax.all._

case class CreateMailboxCommand(
    address: String,
    receivedAt: Long,
    requestId: RequestId
)

object CreateMailboxCommand {
  def from[F[_]](req: CreateMailboxRequest, timestamp: Long, requestId: RequestId)(implicit
      me: MonadError[F, Throwable]
  ): F[CreateMailboxCommand] =
    for {
      addressValidated <- me.fromValidated(validateEmailAddress(req.address))
    } yield CreateMailboxCommand(address = addressValidated, receivedAt = timestamp, requestId = requestId)
}

case class CreateMessageCommand(
    messageId: MessageId,
    receivedAt: Long,
    address: String,
    sender: String,
    subject: String,
    body: String,
    requestId: RequestId
)

object CreateMessageCommand {
  def from[F[_]](
      req: CreateMessageRequest,
      address: String,
      messageId: MessageId,
      timestamp: Long,
      requestId: RequestId
  )(implicit me: MonadError[F, Throwable]): F[CreateMessageCommand] =
    for {
      addressValidated <- me.fromValidated(validateEmailAddress(address))
      senderValidated <- me.fromValidated(validateEmailAddress(req.sender))
    } yield CreateMessageCommand(
      messageId = messageId,
      receivedAt = timestamp,
      address = addressValidated,
      sender = senderValidated,
      subject = req.subject,
      body = req.body,
      requestId = requestId
    )
}

case class MessageCreatedEvent(
    messageId: MessageId,
    messageReceivedAt: Long,
    address: String,
    sender: String,
    subject: String,
    body: String,
    requestId: RequestId,
    messageCreatedAt: Long
)

object MessageCreatedEvent {
  def from[F[_]: Monad](command: CreateMessageCommand, createdAt: Long): F[MessageCreatedEvent] =
    Monad[F].pure(
      MessageCreatedEvent(
        messageId = command.messageId,
        messageReceivedAt = command.receivedAt,
        address = command.address,
        sender = command.sender,
        subject = command.subject,
        body = command.body,
        requestId = command.requestId,
        messageCreatedAt = createdAt
      )
    )
}

case class DeleteMailboxCommand(
    address: String,
    receivedAt: Long,
    requestId: RequestId
)

object DeleteMailboxCommand {
  def from[F[_]](address: String, timestamp: Long, requestId: RequestId)(implicit
      me: MonadError[F, Throwable]
  ): F[DeleteMailboxCommand] =
    for {
      addressValidated <- me.fromValidated(validateEmailAddress(address))
    } yield DeleteMailboxCommand(address = addressValidated, receivedAt = timestamp, requestId = requestId)
}

case class MailboxDeletedEvent(
    address: String,
    requestId: RequestId,
    mailboxDeletedAt: Long,
    deletedMessageCount: Int
)

object MailboxDeletedEvent {
  def from[F[_]: Monad](command: DeleteMailboxCommand, deletedAt: Long, messageCount: Int): F[MailboxDeletedEvent] =
    Monad[F].pure(
      MailboxDeletedEvent(
        address = command.address,
        requestId = command.requestId,
        mailboxDeletedAt = deletedAt,
        deletedMessageCount = messageCount
      )
    )
}

case class DeleteMessageCommand(
    messageId: MessageId,
    receivedAt: Long,
    address: String,
    requestId: RequestId
)

object DeleteMessageCommand {
  def from[F[_]](address: String, messageId: MessageId, timestamp: Long, requestId: RequestId)(implicit
      me: MonadError[F, Throwable]
  ): F[DeleteMessageCommand] =
    for {
      addressValidated <- me.fromValidated(validateEmailAddress(address))
    } yield DeleteMessageCommand(
      messageId = messageId,
      address = addressValidated,
      receivedAt = timestamp,
      requestId = requestId
    )
}

case class MessageDeletedEvent(
    messageId: MessageId,
    messageReceivedAt: Long,
    address: String,
    sender: String,
    subject: String,
    body: String,
    requestId: RequestId,
    messageDeletedAt: Long
)

object MessageDeletedEvent {
  def from[F[_]: Monad](
      command: DeleteMessageCommand,
      receivedAt: Long,
      sender: String,
      subject: String,
      body: String,
      deletedAt: Long
  ): F[MessageDeletedEvent] =
    Monad[F].pure(
      MessageDeletedEvent(
        messageId = command.messageId,
        messageReceivedAt = receivedAt,
        address = command.address,
        sender = sender,
        subject = subject,
        body = body,
        requestId = command.requestId,
        messageDeletedAt = deletedAt
      )
    )
}

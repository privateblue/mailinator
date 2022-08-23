package mailinator.model.write

import mailinator.data.write._
import mailinator.db.read.{MessageIndexView, MessageView}

import cats.syntax.all._
import cats.effect._

trait WriteService[F[_]] {
  def createMailbox(command: CreateMailboxCommand): F[Unit]
  def createMessage(command: CreateMessageCommand): F[MessageCreatedEvent]
  def deleteMailbox(command: DeleteMailboxCommand): F[MailboxDeletedEvent]
  def deleteMessage(command: DeleteMessageCommand): F[MessageDeletedEvent]
}

class DefaultWriteService[F[_]: Async](messageView: MessageView[F], messageIndexView: MessageIndexView[F])
    extends WriteService[F] {
  // This is a no-op in this implementation.
  override def createMailbox(command: CreateMailboxCommand): F[Unit] =
    Async[F].unit

  // Ideally, this would enqueue the command on a message queue, and on the other end workers
  // subscribed to that topic would take the command and process it by updating their views.
  override def createMessage(command: CreateMessageCommand): F[MessageCreatedEvent] =
    for {
      _ <- messageView.appendMessage(
        messageId = command.messageId,
        receivedAt = command.receivedAt,
        recipient = command.address,
        sender = command.sender,
        subject = command.subject,
        body = command.body
      )
      _ <- messageIndexView.appendMessage(
        messageId = command.messageId,
        receivedAt = command.receivedAt,
        recipient = command.address,
        sender = command.sender,
        subject = command.subject
      )
      createdAt <- Async[F].realTimeInstant
      event <- MessageCreatedEvent.from(command, createdAt.toEpochMilli)
    } yield event

  override def deleteMailbox(command: DeleteMailboxCommand): F[MailboxDeletedEvent] =
    for {
      messageRecords <- messageView.removeMailbox(command.address)
      messageIndexRecords <- messageIndexView.removeMailbox(command.address)
      deletedAt <- Async[F].realTimeInstant
      event <- MailboxDeletedEvent.from(command, deletedAt.toEpochMilli, messageRecords.size)
    } yield event

  override def deleteMessage(command: DeleteMessageCommand): F[MessageDeletedEvent] =
    for {
      // the address in the command is ignored as the message id is a unique key
      deleted <- messageView.removeMessage(command.messageId)
      _ <- messageIndexView.removeMessage(command.messageId)
      deletedAt <- Async[F].realTimeInstant
      event <- MessageDeletedEvent.from(
        command,
        deleted.receivedAt,
        deleted.sender,
        deleted.subject,
        deleted.body,
        deletedAt.toEpochMilli
      )
    } yield event
}

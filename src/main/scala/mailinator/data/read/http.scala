package mailinator.data.read

import mailinator.data.shared.{MessageId, MessageIndexViewRecord, MessageViewRecord}
import cats.syntax.all._
import cats.effect.Async

case class ReadMessageIndexResponse(
    messages: Seq[ReadMessageIndexResponseItem],
    nextCursor: Option[Long],
    nextId: Option[MessageId]
)

object ReadMessageIndexResponse {
  def from[F[_]: Async](records: Page[MessageIndexViewRecord, (Long, String)]): F[ReadMessageIndexResponse] =
    for {
      messages <- records.items.map(r => ReadMessageIndexResponseItem.from(r)).sequence
      nextId <- records.next.map(ni => MessageId.from(ni._2)).sequence
    } yield ReadMessageIndexResponse(
      messages = messages,
      nextCursor = records.next.map(_._1),
      nextId = nextId
    )
}

case class ReadMessageIndexResponseItem(
    id: MessageId,
    receivedAt: Long,
    sender: String,
    subject: String
)

object ReadMessageIndexResponseItem {
  def from[F[_]: Async](record: MessageIndexViewRecord): F[ReadMessageIndexResponseItem] =
    for {
      messageId <- MessageId.from(record.messageId)
    } yield ReadMessageIndexResponseItem(
      id = messageId,
      receivedAt = record.receivedAt,
      sender = record.sender,
      subject = record.subject
    )
}

case class ReadMessageResponse(
    id: MessageId,
    receivedAt: Long,
    sender: String,
    subject: String,
    body: String
)

object ReadMessageResponse {
  def from[F[_]: Async](record: MessageViewRecord): F[ReadMessageResponse] =
    for {
      messageId <- MessageId.from(record.messageId)
    } yield ReadMessageResponse(
      id = messageId,
      receivedAt = record.receivedAt,
      sender = record.sender,
      subject = record.subject,
      body = record.body
    )
}

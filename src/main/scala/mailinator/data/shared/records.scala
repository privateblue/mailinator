package mailinator.data.shared

case class MessageIndexViewRecord(
    messageId: String,
    receivedAt: Long,
    recipient: String,
    sender: String,
    subject: String
)

case class MessageViewRecord(
    messageId: String,
    receivedAt: Long,
    recipient: String,
    sender: String,
    subject: String,
    body: String
)

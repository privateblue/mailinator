# Mailinator

This is a possible solution to the exercise detailed [here](https://github.com/fauna/exercises/blob/main/backend.md).

This API consists of 6 endpoints:
- `POST /mailboxes`: This in practice is a no-op. It takes a JSON body: 
```
{
  "address":"{address}"
}
```
and it validates the `address` field to not let invalid email addresses through, but it does nothing, and returns `204`.
 
- `POST /mailboxes/{email address}/messages`: Creates a new message. it takes a JSON body:
```
{
  "sender":"{sender}",
  "subject":"{subject}",
  "body":"{body}"
}
```
it validates the `{email address}` in the URI and the `{sender}` in the JSON, so that both are valid email addresses, creates the message, and returnes a `201` response with a JSON body:
```
{
  "id": {
    "uuid":"{message id}"
  },
  "receivedAt":{timestamp},
  "recipient":"{email address}",
  "sender":"{sender}",
  "subject":"{subject}",
  "body":"{body}",
  "createdAt":{timestamp}
}
```

- `GET /mailboxes/{email address}/messages?timestamp={timestamp}&id={message id}`: Returns an array of message index elements of the specified mailbox, as JSON:
```
{
  "messages": [
    {
      "id": {
        "uuid":"{message id}"
      },
      "receivedAt":{timestamp},
      "sender":"{sender}",
      "subject":"{subject}"
    }
  ],
  "nextCursor":{timestamp},
  "nextId": {
    "uuid":"{message id}"
  }
}
```
The `timestamp` and `id` query params are optional, and serve as a way to retrieve subsequent pages of messages. The `nextCursor` and `nextId` fields of the response are the values to be used as uri params for getting the next page. These are null if there is no next page.

- `GET /mailboxes/{email address}/messages/{message id}`: Retrieves a message by its id, including its body. Returns the folowing JSON:
```
{
"id": {
    "uuid":"{message id}"
  },
  "receivedAt":{timestamp},
  "sender":"{sender}",
  "subject":"{subject}",
  "body":"{body}"
}
```

- `DELETE /mailboxes/{email address}`: Deletes all messages of a mailbox. Returns the following JSON:
```
{
  "address":"{email address}",
  "deletedAt":{timestamp},
  "messageCount":{int}
}
```
The `messageCount` field is a count of all deleted messages.

- `DELETE /mailboxes/{email address}/messages/{message id}`: Deletes a single message by its id. Returns the following JSON:
```
{
  "messageId": {
    "uuid":"{message id}"
  },
  "receivedAt":{timestamp},
  "address":"{email address}",
  "sender":"{sender}",
  "subject":"{subject}",
  "body":"{body}",
  "deletedAt":{timestamp}
}
```
The JSON is practically the deleted message.

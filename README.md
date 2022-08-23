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
 
- `POST /mailboxes/{email address}/messages`: Creates a new message. It takes a JSON body:
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

### Building and running the app

Far the easiest way to build and run the application is to `sbt run` in the cloned directory.

### Design considerations and potential extension directions

###### Deviation from the specification
I've deviated from the exercise spec in one way: a mailbox does not have to be created before sending messages to its address. The API endpoint to create a mailbox is practically a no-op, and at the same time, deleting the last message of an address removes the mailbox too. My reasoning for this deviation is that it just feels slightly more logical, it is closer to how Mailinator actually worked, and also, it simplifies the implemention.

###### Extensibility over simplicity
This solution might feel a bit boilerplate-y at first, and some of the code might be an overkill for such a simple application. What I wanted to demonstrate is how I would organize and modularize any API and its backend.

I was very keen to keep the tiers of the application as separate as possible: the http layer, the service layer and the data storage aspect. These all have their own data models, so that there is no tight coupling between these layers and they can evolve separately, if needed. A lot of the code is just conversion between data classes of these layers, which might feel "bureaucratic", but this actually adds to the type safety, and makes refactorings (swapping out an exteranal lib, switching to a different design pattern, etc.) of one layer easier, as it localizes the interface between layers in a few places.

I also tried to keep the read and write side of the application separate. There are separate components for read and write API endpoints, services, and databases. I didn't go as far as event sourcing, but I kept some CQRS principles in mind, and it wouldn't be hard to evolve this further to a fully fledged event sourced solution.

###### Architecture, and scaling opportunities
This latter consideration is important, because it is unsure if the system is read or write heavy, and it may be necessary to scale these aspects differently. If such a requirement would arise, it would be very easy to split the app, and move read and write components into separate microservices, and run them as separate processes. Obviously, it would require a new datastore implementation.

The architecture I had in my mind, while working on this solution, was such that write requests arrive to a separate write http gateway, which basically just turns these API requests into commands. Ideally these commands would be immediately enqueued on some message queue solution, but I haven't gone that far with my implementation. It would be very easy though to refactor my solution to such a reactive one. 

I've implemented two parallel "views" (database tables), that serve as queryable representations of aggregates, one per corresponding user query - hence the name "view". The system updates these views upon every write request. Ideally, the updaters of these views would be workers somewhere, subscribed to queue topics, and would take commands from the queue, process the commands by updating the views, and enqueue events acknowledging changes and signalling success. In my implementation this isn't done in a reactive fashion, there are no queues and topics, just a simple write service, that initiates write queries directly to the datastore.

The two parallel views are completely redundant - it is just a demonstration of an ideal setup, in which there are eventually consistent views, updated asynchronously on writes, one per user query. For this particular solution one such view (a single database table) would happen to be sufficient, but this wasn't the point.

###### In-memory datastore
I have implemented a naive in-memory datastore, that is based on Akka actors. The motivation here was that actors provide job queues and workers for free, and this makes dealing with concurrent reads and writes much simpler. I was nowhere near of implementing isolation levels, transactions etc., it is really the simplest, most naive implementation of a datastore, with only atomic operations.

The store takes a single `capacity` parameter to bound its size and prevent memory errors, and it evicts the oldest message on every insert if over capacity.

There's an endless numbers of possible improvements to this datastore implementation, but I'm not convinced it is actually worth it. 

(There is also some fairly uggly weirdness around the instantiation of the datastore, that involves existential typing and such. This was a consequence of using parameterized Akka message types, which is not a particularly good idea. It works, but it's clunky.)

###### Missing bits
- There are no configuration files or env vars the app reads, just a `Settings` class with a bunch of hardcoded values, that is passed around.

- There is no logging at all, so the observability of the app is far from ideal.

- In relation to logging, while I added a `RequestId` field to all commands and events, there is no request tracing implemented, although it would be fairly easy to add it.

- The datastore and the API could use monitoring, at least of basic metrics, like RPM, size, memory and CPU utilization, etc.

- Error-handling is very ad-hoc and rudimentary, and it would make the API much nicer if it had its own well-documented error codes.

- Generation of the http protocol from a schema also comes to mind as a nice feature to have, and also versioning of the API in relation to that. 

- I didn't commit to any of the "dependency injection" patterns, so the componenets and modules of the app are wired together simply by passing dependencies in to constructors. It could be refactored into using Scala implicits, or Kleisli etc., but all these have trade-offs, and it was hard to judge at this point what the priorites are, so I rather kept this aspect as simple and basic as possible, so it is easy to change in any direction.

###### Automated tests
I've added a few integration-like tests, that take advantage of the Http4s library's modularity - see `mailinator.IntegrationSpec`. There could be many more such tests to check failure modes, validation, edge-cases etc. 

There is a separate unit test class for the datastore (`store.StoreSpec`), that mainly focuses on correct sorting and pagination, and also the naive eviction mechanism.

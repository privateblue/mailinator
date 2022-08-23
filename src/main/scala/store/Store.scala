package store

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable.{Queue, TreeMap}

case class Store[PK, V, SK, FK](
    map: Map[PK, V],
    sort: TreeMap[(FK, SK, PK), V],
    queue: Queue[PK],
    primaryKey: V => PK,
    sortKey: V => SK,
    filterKey: V => FK,
    capacity: Int
) {
  def composeKey(value: V): (FK, SK, PK) =
    (filterKey(value), sortKey(value), primaryKey(value))

  def append(value: V): (Seq[V], Store[PK, V, SK, FK]) = {
    map
      .get(primaryKey(value))
      .map { _ => (Seq.empty[V] -> this) }
      .getOrElse(
        Seq(value) ->
          Store(
            map + (primaryKey(value) -> value),
            sort + (composeKey(value) -> value),
            queue.enqueue(primaryKey(value)),
            primaryKey,
            sortKey,
            filterKey,
            capacity
          ).evicted()
      )
  }

  def retrieve(key: PK): Seq[V] =
    map.get(key).toSeq

  def retrieveRange(filter: FK, from: Option[(SK, PK)], limit: Option[Int]): Seq[V] =
    sort.keys
      .find(_._1 == filter)
      .flatMap { firstKey =>
        val (fromSK, fromPK) = from.getOrElse((firstKey._2, firstKey._3))
        val key = (filter, fromSK, fromPK)
        sort
          .get(key)
          .map { _ =>
            val results = sort
              .iteratorFrom(key)
              .takeWhile(_._1._1 == filter)
              .map(_._2)
            limit.map(results.take).getOrElse(results).toSeq
          }
      }
      .getOrElse(Seq.empty)

  def remove(key: PK): (Seq[V], Store[PK, V, SK, FK]) = {
    map
      .get(key)
      .map { value =>
        Seq(value) ->
          Store(
            map - key,
            sort - composeKey(value),
            queue.filterNot(_ == key),
            primaryKey,
            sortKey,
            filterKey,
            capacity
          )
      }
      .getOrElse(Seq.empty[V] -> this)
  }

  def evicted(): Store[PK, V, SK, FK] = {
    if (map.size > capacity) {
      val (removedId, newQueue) = queue.dequeue
      val removed = map.get(removedId)
      Store(
        map - removedId,
        removed.map(composeKey).map(sort - _).getOrElse(sort),
        newQueue,
        primaryKey,
        sortKey,
        filterKey,
        capacity
      )
    } else this
  }
}

trait StoreActor {
  type Value
  type PK
  type SK
  type FK

  val primaryKey: Value => PK
  val sortKey: Value => SK
  val filterKey: Value => FK

  implicit val primaryKeyOrdering: Ordering[PK]
  implicit val sortKeyOrdering: Ordering[SK]
  implicit val filterKeyOrdering: Ordering[FK]

  sealed trait Protocol
  case class Append(replyTo: ActorRef[Seq[Value]], value: Value) extends Protocol
  case class Retrieve(replyTo: ActorRef[Seq[Value]], key: PK) extends Protocol
  case class RetrieveRange(replyTo: ActorRef[Seq[Value]], filter: FK, from: Option[(SK, PK)], limit: Option[Int])
      extends Protocol
  case class Remove(replyTo: ActorRef[Seq[Value]], key: PK) extends Protocol

  def empty(capacity: Int): Store[PK, Value, SK, FK] =
    Store(
      Map.empty[PK, Value],
      TreeMap.empty[(FK, SK, PK), Value],
      Queue.empty[PK],
      primaryKey,
      sortKey,
      filterKey,
      capacity
    )

  def apply(capacity: Int): Behavior[Protocol] =
    crd(empty(capacity))

  def crd(store: Store[PK, Value, SK, FK]): Behavior[Protocol] =
    Behaviors.receiveMessage {
      case Append(replyTo, value) =>
        val (result, newStore) = store.append(value)
        replyTo ! result
        crd(newStore)

      case Retrieve(replyTo, key) =>
        val result = store.retrieve(key)
        replyTo ! result
        Behaviors.same

      case RetrieveRange(replyTo, filter, from, limit) =>
        val result = store.retrieveRange(filter, from, limit)
        replyTo ! result
        Behaviors.same

      case Remove(replyTo, key) =>
        val (result, newStore) = store.remove(key)
        replyTo ! result
        crd(newStore)
    }
}

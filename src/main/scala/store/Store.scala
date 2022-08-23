package store

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.collection.immutable.TreeMap

case class Store[PK, V, SK, FK](
    map: Map[PK, V],
    sort: TreeMap[(FK, SK, PK), V],
    primaryKey: V => PK,
    sortKey: V => SK,
    filterKey: V => FK
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
            primaryKey,
            sortKey,
            filterKey
          )
      )
  }

  def retrieve(key: PK): Seq[V] =
    map.get(key).toSeq

  def retrieveRange(filter: FK, from: Option[(SK, PK)], limit: Int): Seq[V] = {
    val firstKey = sort.firstKey
    val (fromSK, fromPK) = from.getOrElse((firstKey._2, firstKey._3))
    sort
      .iteratorFrom((filter, fromSK, fromPK))
      .takeWhile(_._1._1 == filter)
      .map(_._2)
      .take(limit)
      .toSeq
  }

  def remove(key: PK): (Seq[V], Store[PK, V, SK, FK]) = {
    map
      .get(key)
      .map { value =>
        Seq(value) ->
          Store(
            map - key,
            sort - composeKey(value),
            primaryKey,
            sortKey,
            filterKey
          )
      }
      .getOrElse(Seq.empty[V] -> this)
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
  case class RetrieveRange(replyTo: ActorRef[Seq[Value]], filter: FK, from: Option[(SK, PK)], limit: Int)
      extends Protocol
  case class Remove(replyTo: ActorRef[Seq[Value]], key: PK) extends Protocol

  def empty(): Store[PK, Value, SK, FK] =
    Store(
      Map.empty[PK, Value],
      TreeMap.empty[(FK, SK, PK), Value],
      primaryKey,
      sortKey,
      filterKey
    )

  def apply(): Behavior[Protocol] =
    crd(empty())

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

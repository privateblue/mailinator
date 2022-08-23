package mailinator.data.read

case class Page[A, B](
    items: Seq[A],
    next: Option[B]
)

object Page {
  val defaultSize: Int = 5
}

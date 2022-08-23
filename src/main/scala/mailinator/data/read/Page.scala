package mailinator.data.read

case class Page[A, B](
    items: Seq[A],
    next: Option[B]
)

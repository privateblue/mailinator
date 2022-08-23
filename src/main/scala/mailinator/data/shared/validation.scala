package mailinator.data.shared

import cats.data.Validated

import java.util.UUID

object validation {
  def validateUUID(str: String): Validated[Throwable, UUID] =
    Validated.catchNonFatal(
      UUID.fromString(str)
    )
  def validateEmailAddress(str: String): Validated[Throwable, String] =
    Validated.fromOption(
      "^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$".r.findFirstIn(str),
      throw new IllegalArgumentException("Invalid email address")
    )
}

package mailinator.config

import com.comcast.ip4s._

case class Settings(
    host: Host,
    port: Port,
    maxPageSize: Int,
    messageStoreCapacity: Int
)

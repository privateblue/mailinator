package mailinator.http

import mailinator.http.read.ReadHttp
import mailinator.http.write.WriteHttp

import cats.syntax.all._
import cats.effect._

import org.http4s.implicits._
import org.http4s.ember.server._
import org.http4s.server._

import com.comcast.ip4s._

class Server[F[_]: Async](readHttp: ReadHttp[F], writeHttp: WriteHttp[F]) {
  val router = Router[F](
    "/" -> (readHttp.routes <+> writeHttp.routes)
  ).orNotFound

  def instance(address: Host, port: Port) =
    EmberServerBuilder
      .default[F]
      .withHost(address)
      .withPort(port)
      .withHttpApp(router)
      .build
}

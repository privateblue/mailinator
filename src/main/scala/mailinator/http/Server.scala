package mailinator.http

import mailinator.http.read.ReadHttp
import mailinator.http.write.WriteHttp
import mailinator.config.Settings

import cats.syntax.all._
import cats.effect._

import org.http4s.implicits._
import org.http4s.ember.server._
import org.http4s.server._

class Server[F[_]: Async](readHttp: ReadHttp[F], writeHttp: WriteHttp[F], settings: Settings) {
  val router = Router[F](
    "/" -> (readHttp.routes <+> writeHttp.routes)
  ).orNotFound

  def make() =
    EmberServerBuilder
      .default[F]
      .withHost(settings.host)
      .withPort(settings.port)
      .withHttpApp(router)
      .build
}

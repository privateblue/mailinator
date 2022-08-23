package mailinator

import mailinator.db.read._
import mailinator.model.write.DefaultWriteService
import mailinator.http.read.ReadHttp
import mailinator.http.write.WriteHttp
import mailinator.config.Settings

import cats.syntax.all._
import cats.effect._

import com.comcast.ip4s._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    val messageViewResource = StoreActorMessageView.make[IO]
    val messageIndexViewResource = StoreActorMessageIndexView.make[IO]
    (messageViewResource, messageIndexViewResource).parTupled
      .use { case (messageView, messageIndexView) =>
        runMailinator(messageView, messageIndexView)
      }
      .map(_ => ExitCode.Success)
  }

  def runMailinator(
      messageView: MessageView[IO],
      messageIndexView: MessageIndexView[IO]
  ): IO[Unit] = {
    val settings = Settings(
      host = ipv4"0.0.0.0",
      port = port"8080",
      maxPageSize = 5
    )
    val writeService = new DefaultWriteService[IO](messageView, messageIndexView)
    val readHttp = new ReadHttp(messageView, messageIndexView, settings)
    val writeHttp = new WriteHttp(writeService)
    for {
      _ <- new http.Server(readHttp, writeHttp, settings).make().use(_ => IO.never)
    } yield ()
  }

}

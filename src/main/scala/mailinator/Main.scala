package mailinator

import mailinator.db.read._
import mailinator.model.write.WriteServiceMock
import mailinator.http.read.ReadHttp
import mailinator.http.write.WriteHttp

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
    val writeService = new WriteServiceMock[IO](messageView, messageIndexView)
    val readHttp = new ReadHttp(messageView, messageIndexView)
    val writeHttp = new WriteHttp(writeService)
    for {
      _ <- new http.Server(readHttp, writeHttp).instance(ipv4"0.0.0.0", port"8080").use(_ => IO.never)
    } yield ()
  }

}

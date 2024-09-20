package forex

import scala.concurrent.ExecutionContext
import cats.effect._
import forex.config._
import fs2.Stream
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.blaze.server.BlazeServerBuilder

import scala.concurrent.duration.DurationInt

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(executionContext).compile.drain.as(ExitCode.Success)

}

class Application[F[_]: ConcurrentEffect: Timer] {

  def stream(ec: ExecutionContext): Stream[F, Unit] =
    for {
      config <- Config.stream("app")
      clientResource = BlazeClientBuilder[F](ec).resource
      _ <- Stream.resource(clientResource).flatMap { client =>
            val module = new Module[F](config, Some(client))

            val cacheStream = cacheRunner(module)

            BlazeServerBuilder[F](ec)
              .bindHttp(config.http.port, config.http.host)
              .withHttpApp(module.httpApp)
              .serve
              .concurrently(cacheStream)
          }
    } yield ()

  private def cacheRunner(module: Module[F]): Stream[F, Unit] =
    Stream.eval(module.updateRates) ++
      Stream.awakeEvery[F](5.minutes).evalMap { _ =>
        module.updateRates
      }
}

package forex

import cats.effect.{ Concurrent, Sync, Timer }
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import forex.services.rates.interpreters.OneFrameService
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.server.middleware.{ AutoSlash, Timeout }

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig, client: Option[Client[F]] = None) {
  private val validApiKeys: Set[String] = config.services.auth.apiKeys
  private val authMiddleware            = new Authentication[F](validApiKeys)

  private val oneFrameUri: Uri = Uri.unsafeFromString(config.services.oneFrame.baseUrl)
  private val ratesService: RatesService[F] = client match {
    case Some(httpClient) =>
      RatesServices.ofs[F](httpClient, oneFrameUri)
    case None =>
      RatesServices.dummy[F]
  }

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(authMiddleware(http))
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

  def updateRates: F[Unit] = ratesService match {
    case ofs: OneFrameService[F] => ofs.fetchAndCacheAllRates
    case _                       => Sync[F].unit
  }
}

package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private[http] val prefixPathTest = "/test1"

  private val oneFrameServiceGetRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap(Sync[F].fromEither).flatMap { rate =>
        Ok(rate.asGetApiResponse)
      }
  }

  private val httpTestRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      Ok("This is a test API response")
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> oneFrameServiceGetRoutes,
    prefixPathTest -> httpTestRoutes
  )

}

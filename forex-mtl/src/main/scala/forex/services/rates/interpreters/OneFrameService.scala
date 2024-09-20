package forex.services.rates.interpreters

import forex.services.rates.Algebra
import cats.effect.Sync
import cats.implicits.{ catsSyntaxApplicativeError, toFlatMapOps, toFoldableOps, toFunctorOps, toShow }
import com.github.benmanes.caffeine.cache.{ Cache, Caffeine }
import org.http4s.circe.CirceEntityCodec._
import io.circe.generic.auto._
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.rates.errors._
import org.http4s.{ Header, Method, Request, Uri }
import org.http4s.client.Client
import org.slf4j.LoggerFactory
import org.typelevel.ci.CIString

import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

class OneFrameService[F[_]: Sync](client: Client[F], oneFrameUri: Uri) extends Algebra[F] {
  private val logger = LoggerFactory.getLogger(getClass)

  // Cache used to store the 5 mins refresh currency rates.
  private val oneFrameCache: Cache[Rate.Pair, Price] = Caffeine
    .newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .maximumSize(100)
    .build()

  // Response object from OneFrameService
  private case class OneFrameResponse(
      from: String,
      to: String,
      bid: BigDecimal,
      ask: BigDecimal,
      price: BigDecimal,
      time_stamp: OffsetDateTime
  )

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    // Try to get response from the cache first
    Option(oneFrameCache.getIfPresent(pair)) match {
      case Some(price) =>
        // Cache hit: return the rate
        Sync[F].pure(Right(Rate(pair, price, Timestamp(OffsetDateTime.now()))))
      case None =>
        // Cache miss: return an error indicating no rate found
        Sync[F].pure(Left(Error.OneFrameLookupFailed(s"No rate found for $pair")))
    }

  // Initially used for testing proxy functionality.
  // TODO: This is not used. Can remove anytime.
  def getOnce(pair: Rate.Pair): F[Error Either Rate] = {
    val getUri: Uri  = oneFrameUri.withPath(Uri.Path.unsafeFromString("/rates"))
    val customHeader = Header.Raw(CIString("token"), "10dc303535874aeccc86a8251e6992f5")
    val request =
      Request[F](Method.GET, getUri.withQueryParam("pair", s"${pair.from}${pair.to}")).putHeaders(customHeader)
    logger.info(s"Sending request to OneFrame API: $request")
    client.expect[List[OneFrameResponse]](request).attempt.map {
      case Right(responseList) =>
        responseList.headOption match {
          case Some(response) =>
            Right(
              Rate(
                pair = pair,
                price = Price(response.price),
                timestamp = Timestamp(OffsetDateTime.now())
              )
            )
          case None =>
            Left(Error.OneFrameLookupFailed(s"No rate found for ${pair.from}/${pair.to}"))
        }
      case Left(error) =>
        logger.error(s"Failed to retrieve rates for ${pair.from}/${pair.to}: ${error.getMessage}", error)
        Left(Error.OneFrameLookupFailed(s"Failed to retrieve rates for ${pair.from}/${pair.to}: ${error.getMessage}"))
    }
  }

  // Batch call to OneFrameService to get all the possible excahnge rate.
  def fetchAndCacheAllRates: F[Unit] = {
    val allCurrencies: List[Currency] = List(
      Currency.AUD,
      Currency.CAD,
      Currency.CHF,
      Currency.EUR,
      Currency.GBP,
      Currency.NZD,
      Currency.JPY,
      Currency.SGD,
      Currency.USD
    )

    val allPairs: List[Rate.Pair] = for {
      from <- allCurrencies
      to <- allCurrencies if from != to
    } yield Rate.Pair(from, to)

    val pairsParams: Seq[(String, String)] = allPairs.map { pair =>
      "pair" -> s"${pair.from.show}${pair.to.show}"
    }

    val paramsMap: Map[String, Seq[String]] = Map(
      "pair" -> pairsParams.map(_._2)
    )

    val getUri: Uri = oneFrameUri
      .withPath(Uri.Path.unsafeFromString("/rates"))
      .withMultiValueQueryParams(paramsMap)

    // This can extend in future if token for OneFrameService is not fixed.
    val customHeader = Header.Raw(CIString("token"), "10dc303535874aeccc86a8251e6992f5")
    val request      = Request[F](Method.GET, getUri).putHeaders(customHeader)

    client.expect[List[OneFrameResponse]](request).attempt.flatMap {
      case Right(responseList) =>
        // For each response, update the cache
        responseList.traverse_ { response: OneFrameResponse =>
          val fromCurrency = Currency.fromString(response.from)
          val toCurrency   = Currency.fromString(response.to)
          val pair         = Rate.Pair(fromCurrency, toCurrency)
          val price        = Price(response.price)
          Sync[F].delay(oneFrameCache.put(pair, price))
        }
      case Left(error) =>
        logger.error(s"Failed to retrieve rates for cache: ${error.getMessage}", error)
        Sync[F].unit
    }
  }
}

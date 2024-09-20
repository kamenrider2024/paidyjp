package forex

import cats.data.{ Kleisli, OptionT }
import cats.effect.Sync
import com.github.benmanes.caffeine.cache.{ Cache, Caffeine, Expiry }
import org.http4s.{ HttpRoutes, Request, Response, Status }
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.CIString

import java.util.concurrent.TimeUnit

class Authentication[F[_]: Sync](validApiKeys: Set[String]) extends Http4sDsl[F] {
  private val forexAuthRateLimit: Cache[String, Integer] = Caffeine
    .newBuilder()
    .expireAfter(new Expiry[String, Integer]() {
      override def expireAfterCreate(key: String, value: Integer, currentTime: Long): Long =
        TimeUnit.DAYS.toNanos(1)
      override def expireAfterUpdate(key: String, value: Integer, currentTime: Long, currentDuration: Long): Long =
        currentDuration
      override def expireAfterRead(key: String, value: Integer, currentTime: Long, currentDuration: Long): Long =
        currentDuration
    })
    .maximumSize(10)
    .build()

  def apply(routes: HttpRoutes[F]): HttpRoutes[F] = Kleisli { req: Request[F] =>
    // Check if the request contains the correct authentication token
    req.headers.get(CIString("token")) match {
      case Some(header) if validApiKeys.contains(header.head.value) =>
        val token = header.head.value
        // Token is correct, Now check the usage.
        Option(forexAuthRateLimit.getIfPresent(token)) match {
          case Some(count) if count < 10000 =>
            // Token has used in one day, check the usage
            forexAuthRateLimit.put(token, count + 1)
            routes(req)
          case Some(count) if count < 10000 =>
            // API token over used
            OptionT.some(Response[F](Status.Unauthorized).withEntity("Invalid API key. Usage exceed."))
          case None =>
            forexAuthRateLimit.put(header.head.value, 1)
            routes(req)
          case Some(_) =>
            // Default case to handle any unforeseen cases
            OptionT.some(Response[F](Status.Unauthorized).withEntity("Invalid API token usage."))
        }
      case _ =>
        OptionT.some(Response[F](Status.Unauthorized).withEntity("Invalid or missing API key"))
    }
  }
}

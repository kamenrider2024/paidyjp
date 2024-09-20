package forex.config

import scala.concurrent.duration.FiniteDuration

/**
  * Read configs from forex-mtl/src/main/resources/application.conf
  */
case class ApplicationConfig(
    http: HttpConfig,
    services: ExternalServiceConfig,
    currencies: List[String]
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

// Configuration setup for external services like oneFrame
case class ExternalServiceConfig(
    // if we have large number of currency and want to connect to some cache service
    cache: ServiceConfig,
    oneFrame: ServiceConfig,
    auth: AuthConfig
)

case class ServiceConfig(
    baseUrl: String
)

case class AuthConfig(
    apiKeys: Set[String]
)

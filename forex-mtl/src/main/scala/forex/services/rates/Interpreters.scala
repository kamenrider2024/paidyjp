package forex.services.rates

import cats.Applicative
import cats.effect.Sync
import interpreters._
import org.http4s.Uri
import org.http4s.client.Client

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F]                             = new OneFrameDummy[F]()
  def ofs[F[_]: Sync](client: Client[F], oneFrameUri: Uri): Algebra[F] = new OneFrameService[F](client, oneFrameUri)
}

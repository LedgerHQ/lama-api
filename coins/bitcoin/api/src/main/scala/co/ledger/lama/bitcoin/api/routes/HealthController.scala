package co.ledger.lama.bitcoin.api.routes

import cats.effect.{ContextShift, IO, Timer}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.protobuf.lama.common.HealthCheckResponse.ServingStatus
import co.ledger.protobuf.lama.common.HealthFs2Grpc
import co.ledger.protobuf.lama.common._
import io.circe.{Encoder, Json}
import io.grpc.Metadata
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityCodec._
import cats.syntax.all._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

object HealthController extends Http4sDsl[IO] with IOLogging {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  implicit val t: Timer[IO]         = IO.timer(ExecutionContext.global)

  case class HealthStatus(service: String, status: ServingStatus)

  implicit val statusEncoder: Encoder[ServingStatus] =
    (s: ServingStatus) => Json.fromString(s.name)

  private def getServingStatus(client: HealthFs2Grpc[IO, Metadata]): IO[ServingStatus] =
    client
      .check(new HealthCheckRequest(), new Metadata)
      .timeout(5.seconds)
      .handleErrorWith(_ => IO.pure(HealthCheckResponse(ServingStatus.NOT_SERVING)))
      .map(_.status)

  def routes(
      accountManagerHealthClient: HealthFs2Grpc[IO, Metadata],
      interpreterHealthClient: HealthFs2Grpc[IO, Metadata],
      transactorHealthClient: HealthFs2Grpc[IO, Metadata],
      workerHealthClient: HealthFs2Grpc[IO, Metadata],
      keychainHealthClient: HealthFs2Grpc[IO, Metadata]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case GET -> Root =>
      Map(
        "interpreter"     -> getServingStatus(interpreterHealthClient),
        "account_manager" -> getServingStatus(accountManagerHealthClient),
        "transactor"      -> getServingStatus(transactorHealthClient),
        "worker"          -> getServingStatus(workerHealthClient),
        "keychain"        -> getServingStatus(keychainHealthClient)
      ).parUnorderedSequence
        .flatMap { statuses =>
          if (statuses.values.exists(_ != ServingStatus.SERVING))
            InternalServerError(statuses)
          else
            Ok(statuses)
        }
    }
}

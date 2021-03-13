package co.ledger.lama.bitcoin.api

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.api.middlewares.LoggingMiddleware._
import co.ledger.lama.bitcoin.common.clients.grpc.{
  InterpreterGrpcClient,
  KeychainGrpcClient,
  TransactorGrpcClient
}
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import Config.Config
import co.ledger.lama.bitcoin.api.routes.{AccountController, HealthController, VersionController}
import co.ledger.lama.common.clients.grpc.AccountManagerGrpcClient
import co.ledger.protobuf.lama.common.HealthFs2Grpc
import io.grpc.ManagedChannel
import org.http4s.server.middleware._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import pureconfig.ConfigSource
import cats.implicits._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

object App extends IOApp {

  case class ServiceResources(
      accountManagerGrpcChannel: ManagedChannel,
      interpreterGrpcChannel: ManagedChannel,
      transactorGrpcChannel: ManagedChannel,
      workerGrpcChannel: ManagedChannel,
      keychainGrpcChannel: ManagedChannel
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

      accountManagerGrpcChannel <- grpcManagedChannel(conf.accountManager)
      interpreterGrpcChannel    <- grpcManagedChannel(conf.bitcoin.interpreter)
      transactorGrpcChannel     <- grpcManagedChannel(conf.bitcoin.transactor)
      workerGrpcChannel         <- grpcManagedChannel(conf.bitcoin.worker)
      keychainGrpcChannel       <- grpcManagedChannel(conf.bitcoin.keychain)

    } yield ServiceResources(
      accountManagerGrpcChannel = accountManagerGrpcChannel,
      interpreterGrpcChannel = interpreterGrpcChannel,
      transactorGrpcChannel = transactorGrpcChannel,
      workerGrpcChannel = workerGrpcChannel,
      keychainGrpcChannel = keychainGrpcChannel
    )

    resources.use { res =>
      val methodConfig = CORSConfig(
        anyOrigin = true,
        anyMethod = true,
        allowCredentials = false,
        maxAge = 1.day.toSeconds
      )

      val accountManager = new AccountManagerGrpcClient(res.accountManagerGrpcChannel)

      val httpRoutes = Router[IO](
        "accounts" -> CORS(
          loggingMiddleWare(
            AccountController
              .routes(
                new KeychainGrpcClient(res.keychainGrpcChannel),
                accountManager,
                new InterpreterGrpcClient(res.interpreterGrpcChannel)
              ) <+> AccountController
              .transactionsRoutes(
                accountManager,
                new TransactorGrpcClient(res.transactorGrpcChannel)
              )
          ),
          methodConfig
        ),
        "_health" -> CORS(
          HealthController.routes(
            HealthFs2Grpc.stub[IO](res.accountManagerGrpcChannel),
            HealthFs2Grpc.stub[IO](res.interpreterGrpcChannel),
            HealthFs2Grpc.stub[IO](res.transactorGrpcChannel),
            HealthFs2Grpc.stub[IO](res.workerGrpcChannel),
            HealthFs2Grpc.stub[IO](res.keychainGrpcChannel)
          ),
          methodConfig
        ),
        "_version" -> CORS(VersionController.routes(), methodConfig)
      ).orNotFound

      BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(conf.server.port, conf.server.host)
        .withHttpApp(httpRoutes)
        .serve
        .compile
        .drain
        .as(ExitCode.Success)
    }
  }

}

package co.ledger.lama.bitcoin.worker

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterGrpcClient, KeychainGrpcClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerHttpClient
import co.ledger.lama.bitcoin.worker.config.Config
import co.ledger.lama.bitcoin.worker.services._
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.services.Clients
import co.ledger.lama.common.services.grpc.HealthService
import co.ledger.lama.common.models.Coin
import co.ledger.lama.common.utils.{RabbitUtils, ResourceUtils}
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import dev.profunktor.fs2rabbit.interpreter.RabbitClient
import dev.profunktor.fs2rabbit.model.ExchangeType
import io.grpc.{ManagedChannel, Server}
import org.http4s.client.Client
import pureconfig.ConfigSource

object App extends IOApp with IOLogging {

  case class WorkerResources(
      rabbitClient: RabbitClient[IO],
      httpClient: Client[IO],
      keychainGrpcChannel: ManagedChannel,
      interpreterGrpcChannel: ManagedChannel,
      server: Server
  )

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {
      httpClient             <- Clients.htt4s
      keychainGrpcChannel    <- grpcManagedChannel(conf.keychain)
      interpreterGrpcChannel <- grpcManagedChannel(conf.interpreter)
      rabbitClient           <- Clients.rabbit(conf.rabbit)

      serviceDefinitions = List(new HealthService().definition)

      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)

    } yield WorkerResources(
      rabbitClient,
      httpClient,
      keychainGrpcChannel,
      interpreterGrpcChannel,
      grcpService
    )

    resources.use { res =>
      val syncEventService = new RabbitSyncEventService(
        res.rabbitClient,
        conf.queueName(conf.workerEventsExchangeName),
        conf.lamaEventsExchangeName,
        conf.routingKey
      )

      val keychainClient    = new KeychainGrpcClient(res.keychainGrpcChannel)
      val interpreterClient = new InterpreterGrpcClient(res.interpreterGrpcChannel)
      val explorerClient    = new ExplorerHttpClient(res.httpClient, conf.explorer, _)

      val cursorStateService: Coin => CursorStateService[IO] =
        c => CursorStateService(explorerClient(c), interpreterClient).getLastValidState(_, _)

      val worker = new Worker(
        syncEventService,
        keychainClient,
        explorerClient,
        interpreterClient,
        cursorStateService,
        conf.maxTxsToSavePerBatch,
        conf.maxConcurrent
      )

      for {
        _ <- RabbitUtils.declareExchanges(
          res.rabbitClient,
          List(
            (conf.workerEventsExchangeName, ExchangeType.Topic),
            (conf.lamaEventsExchangeName, ExchangeType.Topic)
          )
        )
        _ <- RabbitUtils.declareBindings(
          res.rabbitClient,
          List(
            (
              conf.workerEventsExchangeName,
              conf.routingKey,
              conf.queueName(conf.workerEventsExchangeName)
            ),
            (
              conf.lamaEventsExchangeName,
              conf.routingKey,
              conf.queueName(conf.lamaEventsExchangeName)
            )
          )
        )

        _ <- IO(res.server.start()) *> log.info("Worker started")

        res <- worker.run.compile.lastOrError.as(ExitCode.Success)
      } yield res
    }
  }

}

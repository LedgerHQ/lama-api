package co.ledger.lama.bitcoin.transactor

import cats.effect.{ExitCode, IO, IOApp}
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterGrpcClient, KeychainGrpcClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerHttpClient
import co.ledger.lama.bitcoin.transactor.clients.grpc.BitcoinLibGrpcClient
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.services.Clients
import co.ledger.lama.common.services.grpc.HealthService
import co.ledger.lama.common.utils.ResourceUtils
import co.ledger.lama.common.utils.ResourceUtils.grpcManagedChannel
import fs2._
import pureconfig.ConfigSource

object App extends IOApp with IOLogging {

  def run(args: List[String]): IO[ExitCode] = {
    val conf = ConfigSource.default.loadOrThrow[Config]

    val resources = for {

      interpreterGrpcChannel <- grpcManagedChannel(conf.interpreter)
      keychainGrpcChannel    <- grpcManagedChannel(conf.keychain)
      bitcoinLibGrpcChannel  <- grpcManagedChannel(conf.bitcoinLib)
      httpClient             <- Clients.htt4s

      interpreterService = new InterpreterGrpcClient(interpreterGrpcChannel)
      keychainService    = new KeychainGrpcClient(keychainGrpcChannel)
      explorerService    = new ExplorerHttpClient(httpClient, conf.explorer, _)
      bitcoinLib         = new BitcoinLibGrpcClient(bitcoinLibGrpcChannel)

      serviceDefinitions = List(
        new TransactorGrpcService(
          new Transactor(
            bitcoinLib,
            explorerService,
            keychainService,
            interpreterService,
            conf.transactor
          )
        ).definition,
        new HealthService().definition
      )

      grcpService <- ResourceUtils.grpcServer(conf.grpcServer, serviceDefinitions)

    } yield grcpService

    Stream
      .resource(resources)
      .evalMap(server => IO(server.start()) *> log.info("Transactor started"))
      .evalMap(_ => IO.never)
      .compile
      .drain
      .as(ExitCode.Success)
  }

}

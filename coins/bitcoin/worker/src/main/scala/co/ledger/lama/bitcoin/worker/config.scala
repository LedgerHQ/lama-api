package co.ledger.lama.bitcoin.worker

import cats.implicits._
import co.ledger.lama.bitcoin.common.config.ExplorerConfig
import co.ledger.lama.common.utils.{GrpcClientConfig, GrpcServerConfig}
import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig}
import dev.profunktor.fs2rabbit.model.{ExchangeName, QueueName, RoutingKey}
import pureconfig.ConfigReader
import pureconfig.generic.semiauto._
import pureconfig.module.cats._

object config {

  case class Config(
      workerEventsExchangeName: ExchangeName,
      lamaEventsExchangeName: ExchangeName,
      rabbit: Fs2RabbitConfig,
      explorer: ExplorerConfig,
      keychain: GrpcClientConfig,
      interpreter: GrpcClientConfig,
      grpcServer: GrpcServerConfig
  ) {
    val routingKey: RoutingKey = RoutingKey("bitcoin.*")

    def queueName(exchangeName: ExchangeName): QueueName =
      QueueName(s"${exchangeName.value}.bitcoin")

    val maxConcurrent: Int = 50 // TODO : bench [Runtime.getRuntime.availableProcessors() * x]

    val maxTxsToSavePerBatch: Int = 100 // to avoid gRPC message exceeds maximum size
  }

  object Config {
    implicit val configReader: ConfigReader[Config] = deriveReader[Config]
    implicit val exchangeNameConfigReader: ConfigReader[ExchangeName] =
      ConfigReader.fromString(str => Right(ExchangeName(str)))
    implicit val rabbitNodeConfigReader: ConfigReader[Fs2RabbitNodeConfig] =
      deriveReader[Fs2RabbitNodeConfig]
    implicit val rabbitConfigReader: ConfigReader[Fs2RabbitConfig] = deriveReader[Fs2RabbitConfig]
  }

}

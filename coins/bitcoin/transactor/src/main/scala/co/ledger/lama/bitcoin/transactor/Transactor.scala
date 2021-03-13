package co.ledger.lama.bitcoin.transactor

import cats.data.Validated
import cats.effect.IO
import cats.implicits._
import co.ledger.lama.bitcoin.common.clients.grpc.{InterpreterClient, KeychainClient}
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient
import co.ledger.lama.bitcoin.common.models.interpreter.{ChangeType, Utxo}
import co.ledger.lama.bitcoin.common.models.transactor._
import co.ledger.lama.bitcoin.common.models.{Address, BitcoinLikeNetwork, InvalidAddress}
import co.ledger.lama.bitcoin.common.utils.CoinImplicits._
import co.ledger.lama.bitcoin.transactor.Transactor.ValidationResult
import co.ledger.lama.bitcoin.transactor.clients.grpc.BitcoinLibClient
import co.ledger.lama.bitcoin.transactor.models.RawTxAndUtxos
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib.SignatureMetadata
import co.ledger.lama.bitcoin.transactor.services.{CoinSelectionService, TransactionBytes}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{BitcoinLikeCoin, Coin, Sort}
import fs2.{Chunk, Stream}
import io.circe.syntax._

import java.util.UUID

class Transactor(
    bitcoinLibClient: BitcoinLibClient,
    explorerClient: Coin => ExplorerClient,
    keychainClient: KeychainClient,
    interpreterClient: InterpreterClient,
    conf: TransactorConfig
) extends IOLogging {

  def createTransaction(
      accountId: UUID,
      keychainId: UUID,
      outputs: List[PrepareTxOutput],
      coin: BitcoinLikeCoin,
      coinSelection: CoinSelectionStrategy,
      feeLevel: FeeLevel,
      customFeePerKb: Option[Long],
      maxUtxos: Int
  ): IO[CreateTransactionResponse] =
    for {

      accountInfo <- keychainClient.getKeychainInfo(keychainId)

      utxos <- getUTXOs(accountId, 100, Sort.Ascending).compile.toList
      _ <- log.info(
        s"""Utxos found for account $accountId:
            - number of utxos: ${utxos.size}
            - sum: ${utxos.map(_.value).sum}
         """
      )

      estimatedFeePerKb <- customFeePerKb match {
        case Some(custom) => log.info(s"Custom fee: $custom") *> IO.pure(custom)
        case _            =>
          // TODO: testnet smart fees is buggy on explorer v3
          for {
            smartFee <- coin match {
              case Coin.BtcTestnet => IO.pure(25642L)
              case Coin.BtcRegtest => IO.pure(25642L)
              case c               => explorerClient(c).getSmartFees.map(_.getValue(feeLevel))
            }
            _ <- log.info(
              s"Account($accountId) GetSmartFees feeLevel: $feeLevel - feeSatPerKb: $smartFee "
            )
          } yield smartFee
      }

      changeAddress <- keychainClient
        .getFreshAddresses(keychainId, ChangeType.Internal, 1)
        .flatMap { addresses =>
          IO.fromOption(addresses.headOption)(
            new NoSuchElementException(
              s"Could not get fresh change address from keychain with id : $keychainId"
            )
          )
        }

      estimatedPerByte = estimatedFeePerKb / 1000

      estimatedFeePerUtxo = TransactionBytes.estimateSingleUtxoBytesSize(coin)(
        accountInfo.scheme
      ) * estimatedPerByte

      estimatedFee = TransactionBytes.estimateTxBytesSize(coin)(
        outputs.size
      ) * estimatedPerByte

      targetAmount = outputs.map(_.value).sum + estimatedFee

      response <- createRawTransactionRec(
        coin.toNetwork,
        coinSelection,
        utxos,
        outputs,
        changeAddress.accountAddress,
        estimatedFeePerKb,
        targetAmount,
        estimatedFeePerUtxo,
        if (maxUtxos == 0) conf.maxUtxos else maxUtxos
      )
    } yield {
      CreateTransactionResponse(
        response.rawTx.hex,
        response.rawTx.hash,
        response.rawTx.witnessHash,
        response.utxos,
        estimatedFee,
        estimatedFeePerKb
      )
    }

  def generateSignatures(
      rawTransaction: RawTransaction,
      utxos: List[Utxo],
      privKey: String
  ): IO[List[Array[Byte]]] =
    bitcoinLibClient.generateSignatures(
      rawTransaction,
      utxos,
      privKey
    )

  def broadcastTransaction(
      keychainId: UUID,
      rawTransaction: RawTransaction,
      derivations: List[List[Int]],
      signatures: List[Array[Byte]],
      coin: Coin
  ): IO[RawTransaction] = {
    for {
      pubKeys <- keychainClient.getAddressesPublicKeys(
        keychainId,
        derivations
      )

      _ <- log.info(s"Get pub keys $pubKeys")

      signedRawTx <- bitcoinLibClient.signTransaction(
        rawTransaction,
        coin.toNetwork,
        signatures
          .zip(pubKeys)
          .map { case (signature, pubKey) =>
            SignatureMetadata(
              signature,
              pubKey
            )
          }
      )

      _ <- log.info(
        s"""Signed transaction:
            - coin: ${coin.name}
            - signed hex: ${signedRawTx.hex}
            - tx hash: ${signedRawTx.hash}
         """
      )

      broadcastTxHash <- explorerClient(coin).broadcastTransaction(signedRawTx.hex)

      _ <- log.info(s"Broadcasted tx hash: $broadcastTxHash")

      _ <-
        if (signedRawTx.hash != broadcastTxHash)
          IO.raiseError(
            new Exception(
              s"Signed tx hash is not equal to broadcast tx hash: ${signedRawTx.hash} != $broadcastTxHash"
            )
          )
        else IO.unit

    } yield {
      RawTransaction(
        signedRawTx.hex,
        broadcastTxHash,
        signedRawTx.witnessHash
      )
    }
  }

  def validateAddresses(
      coin: Coin,
      addresses: Seq[Address]
  ): IO[ValidationResult[Address]] = {

    val validateAddress = bitcoinLibClient.validateAddress(_, coin.toNetwork)

    addresses.toList
      .traverse(validateAddress)
  }

  private def createRawTransactionRec(
      network: BitcoinLikeNetwork,
      strategy: CoinSelectionStrategy,
      utxos: List[Utxo],
      outputs: List[PrepareTxOutput],
      changeAddress: String,
      estimatedFeeSatPerKb: Long,
      amount: BigInt,
      feesPerUtxo: BigInt,
      maxUtxos: Int,
      retryCount: Int = 5
  ): IO[RawTxAndUtxos] =
    for {
      _ <-
        if (retryCount <= 0)
          IO.raiseError(
            new Exception(s"""Impossible to create raw transaction satisfying criterias :
                utxos : ${utxos.asJson}
                outputs: ${outputs.asJson}
                estimatedFeeSatPerKb: $estimatedFeeSatPerKb
              """)
          )
        else IO.unit

      selectedUtxos <- IO.fromEither(
        CoinSelectionService.coinSelection(
          strategy,
          utxos,
          amount,
          feesPerUtxo,
          maxUtxos
        )
      )
      _ <- log.info(
        s"""Picked Utxos :
            - number of utxos : ${selectedUtxos.size}
            - sum : ${selectedUtxos.map(_.value).sum}
         """
      )

      _ <- validateTransaction(selectedUtxos, outputs)

      response <- bitcoinLibClient.createTransaction(
        network,
        selectedUtxos,
        outputs,
        changeAddress,
        estimatedFeeSatPerKb,
        0L
      )

      rawTransactionAndUtxos <- response.notEnoughUtxo.fold(
        IO(
          RawTxAndUtxos(
            RawTransaction(
              response.hex,
              response.hash,
              response.witnessHash
            ),
            selectedUtxos
          )
        )
      )(notEnoughUtxo =>
        createRawTransactionRec(
          network,
          strategy,
          utxos,
          outputs,
          changeAddress,
          estimatedFeeSatPerKb,
          amount + notEnoughUtxo.missingAmount,
          feesPerUtxo,
          maxUtxos,
          retryCount - 1
        )
      )
    } yield rawTransactionAndUtxos

  private def getUTXOs(accountId: UUID, limit: Int, sort: Sort): Stream[IO, Utxo] = {
    def getUtxosRec(accountId: UUID, limit: Int, offset: Int, sort: Sort): Stream[IO, Utxo] = {
      Stream
        .eval(interpreterClient.getUtxos(accountId, limit + offset, offset, Some(sort)))
        .flatMap { result =>
          val head = Stream.chunk(Chunk.seq(result.utxos)).covary[IO]

          val tail =
            if (result.truncated)
              getUtxosRec(accountId, limit, offset + limit, sort)
            else
              Stream.empty

          head ++ tail
        }
    }

    getUtxosRec(accountId, limit, 0, sort)
  }

  private def validateTransaction(
      utxos: List[Utxo],
      recipients: List[PrepareTxOutput]
  ): IO[Unit] =
    if (utxos.toList.map(_.value).sum < recipients.map(_.value).sum)
      IO.raiseError(new Exception("Not enough coins in Utxos to cover for coins sent."))
    else
      IO.unit

}

object Transactor {
  type ValidationResult[A] = List[Validated[InvalidAddress, A]]
}

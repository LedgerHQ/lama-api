package co.ledger.lama.bitcoin.transactor.clients.grpc

import cats.data.Validated
import cats.effect.{ContextShift, IO}
import cats.implicits._
import co.ledger.lama.bitcoin.common.models.{Address, BitcoinLikeNetwork, InvalidAddress}
import co.ledger.lama.bitcoin.common.models.interpreter.Utxo
import co.ledger.lama.bitcoin.common.models.transactor.{PrepareTxOutput, RawTransaction}
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib._
import co.ledger.lama.bitcoin.transactor.models.implicits._
import co.ledger.lama.common.clients.grpc.GrpcClient
import co.ledger.lama.common.logging.IOLogging
import co.ledger.protobuf.bitcoin.libgrpc
import io.grpc.{ManagedChannel, Metadata}

trait BitcoinLibClient {

  def validateAddress(
      address: Address,
      network: BitcoinLikeNetwork
  ): IO[Validated[InvalidAddress, Address]]

  def createTransaction(
      network: BitcoinLikeNetwork,
      selectedUtxos: List[Utxo],
      outputs: List[PrepareTxOutput],
      changeAddress: String,
      feeSatPerKb: Long,
      lockTime: Long
  ): IO[RawTransactionResponse]

  def generateSignatures(
      rawTransaction: RawTransaction,
      utxos: List[Utxo],
      privkey: String
  ): IO[List[Array[Byte]]]

  def signTransaction(
      rawTransaction: RawTransaction,
      network: BitcoinLikeNetwork,
      signatures: List[SignatureMetadata]
  ): IO[RawTransactionResponse]
}

class BitcoinLibGrpcClient(val managedChannel: ManagedChannel)(implicit val cs: ContextShift[IO])
    extends BitcoinLibClient
    with IOLogging {

  val client: libgrpc.CoinServiceFs2Grpc[IO, Metadata] =
    GrpcClient.resolveClient(
      libgrpc.CoinServiceFs2Grpc.stub[IO],
      managedChannel,
      "BitcoinLibClient"
    )

  override def validateAddress(
      address: Address,
      network: BitcoinLikeNetwork
  ): IO[Validated[InvalidAddress, Address]] = {

    client
      .validateAddress(
        libgrpc.ValidateAddressRequest(
          address = address.value,
          chainParams = Some(network.toLibGrpcProto)
        ),
        new Metadata
      )
      .map(r =>
        if (r.isValid) Address(r.address).valid
        else InvalidAddress(address, r.invalidReason).invalid
      )
  }

  def createTransaction(
      network: BitcoinLikeNetwork,
      selectedUtxos: List[Utxo],
      outputs: List[PrepareTxOutput],
      changeAddress: String,
      feeSatPerKb: Long,
      lockTime: Long = 0L
  ): IO[RawTransactionResponse] =
    client
      .createTransaction(
        CreateTransactionRequest(
          lockTime.toInt,
          selectedUtxos.map(utxosToInputs),
          outputs.map(prepareTxOutput =>
            Output(
              prepareTxOutput.address,
              prepareTxOutput.value
            )
          ),
          network,
          changeAddress,
          feeSatPerKb
        ).toProto,
        new Metadata
      )
      .map(RawTransactionResponse.fromProto)

  def generateSignatures(
      rawTransaction: RawTransaction,
      utxos: List[Utxo],
      privkey: String
  ): IO[List[Array[Byte]]] =
    client
      .generateDerSignatures(
        libgrpc.GenerateDerSignaturesRequest(
          Some(
            libgrpc.RawTransactionResponse(
              rawTransaction.hex,
              rawTransaction.hash,
              rawTransaction.witnessHash,
              None
            )
          ),
          utxos
            .map(utxo =>
              libgrpc.Utxo(
                utxo.scriptHex,
                utxo.value.toString,
                utxo.derivation.toList
              )
            ),
          privkey
        ),
        new Metadata
      )
      .map(_.derSignatures.map(_.toByteArray).toList)

  def signTransaction(
      rawTransaction: RawTransaction,
      network: BitcoinLikeNetwork,
      signatures: List[SignatureMetadata]
  ): IO[RawTransactionResponse] =
    client
      .signTransaction(
        libgrpc.SignTransactionRequest(
          Some(
            libgrpc.RawTransactionResponse(
              rawTransaction.hex,
              rawTransaction.hash,
              rawTransaction.witnessHash,
              None
            )
          ),
          Some(network.toLibGrpcProto),
          signatures.map(_.toProto)
        ),
        new Metadata
      )
      .map(RawTransactionResponse.fromProto)
      .handleErrorWith(IO.raiseError)

  private def utxosToInputs(utxo: Utxo): Input = {
    Input(
      utxo.transactionHash,
      utxo.outputIndex,
      utxo.scriptHex,
      utxo.value
    )
  }

}

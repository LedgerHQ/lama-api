package co.ledger.lama.bitcoin.transactor.services

import cats.data.Validated
import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter.Utxo
import co.ledger.lama.bitcoin.common.models.{
  Address,
  BitcoinLikeNetwork,
  InvalidAddress,
  interpreter,
  transactor
}
import co.ledger.lama.bitcoin.transactor.clients.grpc.BitcoinLibClient
import co.ledger.lama.bitcoin.transactor.models.bitcoinLib

class BitcoinLibClientServiceMock extends BitcoinLibClient {

  def createTransaction(
      network: BitcoinLikeNetwork,
      selectedUtxos: List[interpreter.Utxo],
      outputs: List[transactor.PrepareTxOutput],
      changeAddress: String,
      feeSatPerKb: Long,
      lockTime: Long
  ): IO[bitcoinLib.RawTransactionResponse] = IO(
    bitcoinLib.RawTransactionResponse(
      "hex",
      "hash",
      "witnessHash",
      None
    )
  )
  def generateSignatures(
      rawTransaction: transactor.RawTransaction,
      utxos: List[Utxo],
      privkey: String
  ): IO[List[Array[Byte]]] = ???

  override def signTransaction(
      rawTransaction: transactor.RawTransaction,
      network: BitcoinLikeNetwork,
      signatures: List[bitcoinLib.SignatureMetadata]
  ): IO[bitcoinLib.RawTransactionResponse] = ???

  override def validateAddress(
      address: Address,
      network: BitcoinLikeNetwork
  ): IO[Validated[InvalidAddress, Address]] =
    IO.delay(Validated.valid(address))
}

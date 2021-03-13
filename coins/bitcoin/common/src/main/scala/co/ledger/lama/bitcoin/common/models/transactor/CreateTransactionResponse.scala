package co.ledger.lama.bitcoin.common.models.transactor

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.common.models.interpreter.Utxo
import co.ledger.lama.bitcoin.transactor.protobuf
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class CreateTransactionResponse(
    hex: String,
    hash: String,
    witnessHash: String,
    utxos: List[Utxo],
    fee: Long,
    feePerKb: Long
) {
  def toProto: protobuf.CreateTransactionResponse =
    protobuf.CreateTransactionResponse(
      hex,
      hash,
      witnessHash,
      utxos.map(_.toProto),
      fee,
      feePerKb
    )
}

object CreateTransactionResponse {
  implicit val encoder: Encoder[CreateTransactionResponse] =
    deriveConfiguredEncoder[CreateTransactionResponse]
  implicit val decoder: Decoder[CreateTransactionResponse] =
    deriveConfiguredDecoder[CreateTransactionResponse]

  def fromProto(proto: protobuf.CreateTransactionResponse): CreateTransactionResponse =
    CreateTransactionResponse(
      proto.hex,
      proto.hash,
      proto.witnessHash,
      proto.utxos.map(Utxo.fromProto).toList,
      proto.fee,
      proto.feePerKb
    )
}

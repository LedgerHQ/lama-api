package co.ledger.lama.bitcoin.common.models.transactor

import co.ledger.lama.common.models.implicits._
import co.ledger.lama.bitcoin.transactor.protobuf
import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}

case class RawTransaction(
    hex: String,
    hash: String,
    witnessHash: String
) {
  def toProto: protobuf.RawTransaction =
    protobuf.RawTransaction(
      hex,
      hash,
      witnessHash
    )
}

object RawTransaction {
  implicit val encoder: Encoder[RawTransaction] =
    deriveConfiguredEncoder[RawTransaction]
  implicit val decoder: Decoder[RawTransaction] =
    deriveConfiguredDecoder[RawTransaction]

  def fromProto(proto: protobuf.RawTransaction): RawTransaction =
    RawTransaction(
      proto.hex,
      proto.hash,
      proto.witnessHash
    )
}

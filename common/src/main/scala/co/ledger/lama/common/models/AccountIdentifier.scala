package co.ledger.lama.common.models

import co.ledger.lama.common.models.implicits._

import java.util.UUID

import io.circe.{Decoder, Encoder}
import io.circe.generic.extras.semiauto._

case class AccountIdentifier(key: String, coinFamily: CoinFamily, coin: Coin) {
  def id: UUID = UUID.nameUUIDFromBytes((key + coinFamily.name + coin.name).getBytes)
}

object AccountIdentifier {
  implicit val encoder: Encoder[AccountIdentifier] = deriveConfiguredEncoder[AccountIdentifier]
  implicit val decoder: Decoder[AccountIdentifier] = deriveConfiguredDecoder[AccountIdentifier]
}

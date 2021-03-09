package co.ledger.lama.common.models.messages

import java.util.UUID

import io.circe.generic.extras.semiauto._
import io.circe.{Decoder, Encoder}

import co.ledger.lama.common.models.{AccountIdentifier, WorkableEvent}
import co.ledger.lama.common.models.implicits._

// TODO: add account group to business ID ?
case class WorkerMessage[T](account: AccountIdentifier, event: WorkableEvent[T])
    extends WithBusinessId[UUID] {
  val businessId: UUID = account.id
}

object WorkerMessage {
  implicit def encoder[T: Encoder]: Encoder[WorkerMessage[T]] =
    deriveConfiguredEncoder[WorkerMessage[T]]

  implicit def decoder[T: Decoder]: Decoder[WorkerMessage[T]] =
    deriveConfiguredDecoder[WorkerMessage[T]]
}

trait WithBusinessId[K] { def businessId: K }

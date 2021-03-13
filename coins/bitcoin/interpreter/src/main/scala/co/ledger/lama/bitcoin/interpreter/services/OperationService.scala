package co.ledger.lama.bitcoin.interpreter.services

import cats.data.{NonEmptyList, OptionT}
import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.interpreter._
import co.ledger.lama.bitcoin.interpreter.models.{OperationToSave, TransactionAmounts}
import co.ledger.lama.bitcoin.interpreter.services.OperationQueries.{
  OpWithoutDetails,
  TransactionDetails
}
import co.ledger.lama.common.logging.IOLogging
import co.ledger.lama.common.models.{Sort, TxHash}
import doobie._
import doobie.implicits._
import fs2._

import java.util.UUID

class OperationService(
    db: Transactor[IO],
    maxConcurrent: Int
) extends IOLogging {

  def getOperations(
      accountId: UUID,
      blockHeight: Long,
      limit: Int,
      offset: Int,
      sort: Sort
  )(implicit cs: ContextShift[IO]): IO[GetOperationsResult] =
    for {
      opsWithTx <- OperationQueries
        .fetchOperations(accountId, blockHeight, sort, Some(limit + 1), Some(offset))
        .groupAdjacentBy(_.op.hash) // many operations by hash (RECEIVED AND SENT)
        .chunkN(OperationService.numberOfOperationsToBuildByQuery)
        .flatMap { ops =>
          val txHashes = ops.map { case (txHash, _) => txHash }.toNel

          val inputsAndOutputs = Stream
            .emits(txHashes.toList)
            .flatMap(txHashes =>
              OperationQueries
                .fetchTransactionDetails(accountId, sort, txHashes)
            )

          Stream
            .chunk(ops)
            .covary[ConnectionIO]
            .zip(inputsAndOutputs)
        }
        .transact(db)
        .through(makeOperation)
        .compile
        .toList

      total <- OperationQueries.countOperations(accountId, blockHeight).transact(db)

      // we get 1 more than necessary to know if there's more, then we return the correct number
      truncated = opsWithTx.size > limit

    } yield {
      val operations = opsWithTx.take(limit)
      GetOperationsResult(operations, total, truncated)
    }

  private lazy val makeOperation: Pipe[
    IO,
    (
        (TxHash, Chunk[OpWithoutDetails]),
        OperationQueries.TransactionDetails
    ),
    Operation
  ] =
    _.flatMap {
      case (
            (txHash, sentAndReceivedOperations),
            inputsWithOutputsByTxHash
          ) =>
        Stream
          .chunk(sentAndReceivedOperations)
          .takeWhile(_ => txHash == inputsWithOutputsByTxHash.txHash)
          .map { op =>
            operation(op, inputsWithOutputsByTxHash)
          }
    }

  def getOperation(
      accountId: Operation.AccountId,
      operationId: Operation.UID
  )(implicit cs: ContextShift[IO]): IO[Option[Operation]] = {

    val o = for {
      opWithTx <- OptionT(OperationQueries.findOperation(accountId, operationId))
      inputsWithOutputsWithTxHash <- OptionT(
        OperationQueries
          .fetchTransactionDetails(
            accountId.value,
            Sort.Ascending,
            NonEmptyList.one(opWithTx.op.hash)
          )
          .compile
          .last
      )
      if inputsWithOutputsWithTxHash.txHash == opWithTx.op.hash
    } yield operation(
      opWithTx,
      inputsWithOutputsWithTxHash
    )

    o.value.transact(db)
  }

  private def operation(
      emptyOperation: OpWithoutDetails,
      inputsWithOutputsByTxHash: TransactionDetails
  ) =
    Operation(
      uid = emptyOperation.op.uid,
      accountId = emptyOperation.op.accountId,
      hash = emptyOperation.op.hash.hex,
      transaction = TransactionView(
        id = emptyOperation.tx.id,
        hash = emptyOperation.tx.hash.hex,
        receivedAt = emptyOperation.tx.receivedAt,
        lockTime = emptyOperation.tx.lockTime,
        fees = emptyOperation.tx.fees,
        inputs = inputsWithOutputsByTxHash.inputs,
        outputs = inputsWithOutputsByTxHash.outputs,
        block = emptyOperation.tx.block,
        confirmations = emptyOperation.tx.confirmations
      ),
      operationType = emptyOperation.op.operationType,
      amount = emptyOperation.op.amount,
      fees = emptyOperation.op.fees,
      time = emptyOperation.op.time,
      blockHeight = emptyOperation.op.blockHeight
    )

  def deleteUnconfirmedOperations(accountId: UUID): IO[Int] =
    OperationQueries
      .deleteUnconfirmedOperations(accountId)
      .transact(db)

  def getUtxos(
      accountId: UUID,
      sort: Sort,
      limit: Int,
      offset: Int
  ): IO[GetUtxosResult] =
    for {
      utxos <- OperationQueries
        .fetchUTXOs(accountId, sort, Some(limit + 1), Some(offset))
        .transact(db)
        .compile
        .toList

      total <- OperationQueries.countUTXOs(accountId).transact(db)

    } yield {
      // We get 1 more than necessary to know if there's more, then we return the correct number
      GetUtxosResult(utxos.slice(0, limit), total, truncated = utxos.size > limit)
    }

  def getUnconfirmedUtxos(accountId: UUID): IO[List[Utxo]] =
    OperationQueries
      .fetchUnconfirmedUTXOs(accountId)
      .transact(db)
      .compile
      .toList

  def removeFromCursor(accountId: UUID, blockHeight: Long): IO[Int] =
    OperationQueries.removeFromCursor(accountId, blockHeight).transact(db)

  def compute(accountId: UUID): Stream[IO, OperationToSave] =
    operationSource(accountId)
      .flatMap(op => Stream.chunk(op.computeOperations))

  private def operationSource(accountId: UUID): Stream[IO, TransactionAmounts] =
    OperationQueries
      .fetchTransactionAmounts(accountId)
      .transact(db)

  def saveOperationSink(implicit cs: ContextShift[IO]): Pipe[IO, OperationToSave, OperationToSave] =
    in =>
      in.chunkN(1000) // TODO : in conf
        .prefetch
        .parEvalMapUnordered(maxConcurrent) { batch =>
          OperationQueries.saveOperations(batch).transact(db).map(_ => batch)
        }
        .flatMap(Stream.chunk)

}

object OperationService {
  val numberOfOperationsToBuildByQuery = 5
}

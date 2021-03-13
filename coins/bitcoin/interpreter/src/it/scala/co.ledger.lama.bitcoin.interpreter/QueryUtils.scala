package co.ledger.lama.bitcoin.interpreter

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.lama.bitcoin.common.models.interpreter.{
  InputView,
  Operation,
  OutputView,
  TransactionView
}
import co.ledger.lama.bitcoin.interpreter.models.OperationToSave
import co.ledger.lama.bitcoin.interpreter.services.{OperationQueries, TransactionQueries}
import co.ledger.lama.common.models.{Sort, TxHash}
import doobie.implicits._
import doobie.util.transactor.Transactor
import fs2.Chunk

import java.util.UUID

object QueryUtils {
  def fetchInputAndOutputs(
      db: Transactor[IO],
      accountId: UUID,
      txHash: TxHash
  ): IO[Option[(List[InputView], List[OutputView])]] = {
    OperationQueries
      .fetchTransactionDetails(accountId, Sort.Descending, NonEmptyList.one(txHash))
      .transact(db)
      .map(io => (io.inputs, io.outputs))
      .compile
      .last
  }

  def fetchOpAndTx(
      db: Transactor[IO],
      accountId: Operation.AccountId,
      operationId: Operation.UID
  ): IO[Option[OperationQueries.OpWithoutDetails]] =
    OperationQueries.findOperation(accountId, operationId).transact(db)

  def saveTx(db: Transactor[IO], transaction: TransactionView, accountId: UUID): IO[Unit] = {
    TransactionQueries
      .saveTransaction(transaction, accountId)
      .transact(db)
      .void
  }

  def fetchOps(db: Transactor[IO], accountId: UUID): IO[List[OperationQueries.OpWithoutDetails]] = {
    OperationQueries
      .fetchOperations(accountId)
      .transact(db)
      .compile
      .toList
  }

  def saveOp(db: Transactor[IO], operation: OperationToSave): IO[Unit] = {
    OperationQueries
      .saveOperations(Chunk(operation))
      .transact(db)
      .void
  }

}

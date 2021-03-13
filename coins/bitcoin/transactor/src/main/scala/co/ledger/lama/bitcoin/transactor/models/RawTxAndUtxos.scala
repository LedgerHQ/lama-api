package co.ledger.lama.bitcoin.transactor.models

import co.ledger.lama.bitcoin.common.models.interpreter.Utxo
import co.ledger.lama.bitcoin.common.models.transactor.RawTransaction

case class RawTxAndUtxos(rawTx: RawTransaction, utxos: List[Utxo])

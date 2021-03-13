package co.ledger.lama.bitcoin.worker

import cats.data.NonEmptyList
import cats.effect.IO
import co.ledger.lama.bitcoin.common.clients.grpc.KeychainClient
import co.ledger.lama.bitcoin.common.clients.http.ExplorerClient.Address
import co.ledger.lama.bitcoin.common.models.interpreter.{AccountAddress, ChangeType}
import co.ledger.lama.bitcoin.common.models.keychain.{AccountKey, KeychainInfo}
import co.ledger.lama.bitcoin.common.models.{BitcoinLikeNetwork, BitcoinNetwork, Scheme}

import java.util.UUID
import scala.collection.mutable

object KeychainFixture {

  trait UsedAddressesTracker {
    val newlyMarkedAddresses: mutable.ArrayDeque[Address] = mutable.ArrayDeque.empty
  }

  def keychainClient(
      addresses: LazyList[Address],
      lookaheadSize: Int = 20
  ): KeychainClient with UsedAddressesTracker =
    new KeychainClient with UsedAddressesTracker {

      override def create(
          accountKey: AccountKey,
          scheme: Scheme,
          lookaheadSize: Int,
          network: BitcoinLikeNetwork
      ): IO[KeychainInfo] = ???

      override def getKeychainInfo(keychainId: UUID): IO[KeychainInfo] =
        IO.delay(
          KeychainInfo(
            keychainId,
            externalDescriptor = "externalDesc",
            internalDescriptor = "internalDesc",
            extendedPublicKey = "extendedPublicKey",
            slip32ExtendedPublicKey = "slip32ExtendedPublicKey",
            lookaheadSize = lookaheadSize,
            scheme = Scheme.Bip44,
            network = BitcoinNetwork.MainNet
          )
        )

      override def getAddresses(
          keychainId: UUID,
          fromIndex: Int,
          toIndex: Int,
          changeType: Option[ChangeType]
      ): IO[List[AccountAddress]] =
        IO.delay(
          addresses
            .slice(fromIndex, toIndex)
            .map(AccountAddress(_, ChangeType.External, derivation = NonEmptyList.one(1)))
            .toList
        )

      override def markAddressesAsUsed(keychainId: UUID, addresses: List[String]): IO[Unit] =
        IO.delay(newlyMarkedAddresses.addAll(addresses))

      override def getFreshAddresses(
          keychainId: UUID,
          change: ChangeType,
          size: Int
      ): IO[List[AccountAddress]] = ???

      override def getAddressesPublicKeys(
          keychainId: UUID,
          derivations: List[List[Int]]
      ): IO[List[String]] = ???

      override def deleteKeychain(keychainId: UUID): IO[Unit] = ???

      override def resetKeychain(keychainId: UUID): IO[Unit] = ???
    }

}

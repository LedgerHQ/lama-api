package co.ledger.lama.bitcoin.common.clients.grpc

import java.util.UUID
import cats.effect.{ContextShift, IO}
import co.ledger.lama.bitcoin.common.models.interpreter.{AccountAddress, ChangeType}
import co.ledger.lama.bitcoin.common.models.keychain.{AccountKey, KeychainInfo}
import co.ledger.lama.bitcoin.common.models.{BitcoinLikeNetwork, Scheme}
import co.ledger.lama.common.clients.grpc.GrpcClient
import co.ledger.lama.common.utils.UuidUtils
import co.ledger.protobuf.bitcoin.keychain
import io.grpc._

trait KeychainClient {
  def create(
      accountKey: AccountKey,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinLikeNetwork
  ): IO[KeychainInfo]

  def getKeychainInfo(keychainId: UUID): IO[KeychainInfo]

  def getAddresses(
      keychainId: UUID,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]]

  def markAddressesAsUsed(keychainId: UUID, addresses: List[String]): IO[Unit]

  def getFreshAddresses(keychainId: UUID, change: ChangeType, size: Int): IO[List[AccountAddress]]

  def getAddressesPublicKeys(
      keychainId: UUID,
      derivations: List[List[Int]]
  ): IO[List[String]]

  def resetKeychain(keychainId: UUID): IO[Unit]

  def deleteKeychain(keychainId: UUID): IO[Unit]
}

class KeychainGrpcClient(
    val managedChannel: ManagedChannel
)(implicit val cs: ContextShift[IO])
    extends KeychainClient {

  val client: keychain.KeychainServiceFs2Grpc[IO, Metadata] =
    GrpcClient.resolveClient(
      keychain.KeychainServiceFs2Grpc.stub[IO],
      managedChannel,
      "keychainClient"
    )

  def create(
      accountKey: AccountKey,
      scheme: Scheme,
      lookaheadSize: Int,
      network: BitcoinLikeNetwork
  ): IO[KeychainInfo] =
    client
      .createKeychain(
        keychain.CreateKeychainRequest(
          accountKey.toProto,
          scheme.toProto,
          lookaheadSize,
          Some(network.toKeychainChainParamsProto)
        ),
        new Metadata
      )
      .map(KeychainInfo.fromProto)

  def getKeychainInfo(keychainId: UUID): IO[KeychainInfo] =
    client
      .getKeychainInfo(
        keychain.GetKeychainInfoRequest(UuidUtils.uuidToBytes(keychainId)),
        new Metadata
      )
      .map(KeychainInfo.fromProto)

  def getAddresses(
      keychainId: UUID,
      fromIndex: Int,
      toIndex: Int,
      changeType: Option[ChangeType] = None
  ): IO[List[AccountAddress]] =
    client
      .getAllObservableAddresses(
        changeType // not the best way to handle...
          .map(change =>
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId),
              fromIndex = fromIndex,
              toIndex = toIndex,
              change = change.toKeychainProto
            )
          )
          .getOrElse(
            keychain.GetAllObservableAddressesRequest(
              keychainId = UuidUtils.uuidToBytes(keychainId),
              fromIndex = fromIndex,
              toIndex = toIndex
            )
          ),
        new Metadata
      )
      .map(_.addresses.map(AccountAddress.fromKeychainProto).toList)

  def markAddressesAsUsed(keychainId: UUID, addresses: List[String]): IO[Unit] =
    client
      .markAddressesAsUsed(
        keychain.MarkAddressesAsUsedRequest(UuidUtils.uuidToBytes(keychainId), addresses),
        new Metadata
      )
      .void

  def getFreshAddresses(keychainId: UUID, change: ChangeType, size: Int): IO[List[AccountAddress]] =
    client
      .getFreshAddresses(
        keychain.GetFreshAddressesRequest(
          UuidUtils.uuidToBytes(keychainId),
          change.toKeychainProto,
          size
        ),
        new Metadata
      )
      .map(_.addresses.map(AccountAddress.fromKeychainProto).toList)

  def getAddressesPublicKeys(
      keychainId: UUID,
      derivations: List[List[Int]]
  ): IO[List[String]] =
    client
      .getAddressesPublicKeys(
        keychain.GetAddressesPublicKeysRequest(
          UuidUtils.uuidToBytes(keychainId),
          derivations
            .map(derivation => keychain.DerivationPath(derivation))
        ),
        new Metadata
      )
      .map(_.publicKeys.toList)

  def resetKeychain(keychainId: UUID): IO[Unit] =
    client
      .resetKeychain(
        keychain.ResetKeychainRequest(
          keychainId = UuidUtils.uuidToBytes(keychainId)
        ),
        new Metadata
      )
      .void

  def deleteKeychain(keychainId: UUID): IO[Unit] =
    client
      .deleteKeychain(
        keychain.DeleteKeychainRequest(
          keychainId = UuidUtils.uuidToBytes(keychainId)
        ),
        new Metadata
      )
      .void
}

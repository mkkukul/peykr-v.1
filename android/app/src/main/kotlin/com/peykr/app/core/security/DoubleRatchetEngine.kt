package com.peykr.app.core.security

import java.security.KeyFactory
import java.security.KeyPair
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class RatchetHeader(
  val senderDhPublicKeyBase64: String,
  val sequenceNumber: Int,
  val previousChainLength: Int,
)

data class EncryptedRatchetMessage(
  val header: RatchetHeader,
  val ivBase64: String,
  val ciphertextWithTagBase64: String,
)

data class RatchetSessionState(
  val contactId: String,
  val rootKey: ByteArray,
  val sendingChainKey: ByteArray?,
  val receivingChainKey: ByteArray?,
  val localDhKeyPair: KeyPair,
  val remoteDhPublicKey: PublicKey?,
  val sendCounter: Int = 0,
  val receiveCounter: Int = 0,
  val previousChainLength: Int = 0,
)

class DoubleRatchetEngine {

  fun initializeInitiator(
    contactId: String,
    sharedMasterSecret: ByteArray,
    remoteDhPublicKey: PublicKey,
  ): RatchetSessionState {
    val localKeyPair = CryptoPrimitives.generateEcdhKeyPair()
    val dhShared = CryptoPrimitives.computeSharedSecret(localKeyPair.private, remoteDhPublicKey)
    val derived = CryptoPrimitives.hkdf(
      salt = sharedMasterSecret,
      ikm = dhShared,
      info = KDF_RK_INFO,
      length = 64,
    )
    val rootKey = derived.copyOfRange(0, 32)
    val sendingChainKey = derived.copyOfRange(32, 64)

    return RatchetSessionState(
      contactId = contactId,
      rootKey = rootKey,
      sendingChainKey = sendingChainKey,
      receivingChainKey = null,
      localDhKeyPair = localKeyPair,
      remoteDhPublicKey = remoteDhPublicKey,
      sendCounter = 0,
      receiveCounter = 0,
    )
  }

  fun initializeResponder(
    contactId: String,
    sharedMasterSecret: ByteArray,
    localKeyPair: KeyPair,
  ): RatchetSessionState {
    return RatchetSessionState(
      contactId = contactId,
      rootKey = sharedMasterSecret.copyOf(32),
      sendingChainKey = null,
      receivingChainKey = null,
      localDhKeyPair = localKeyPair,
      remoteDhPublicKey = null,
      sendCounter = 0,
      receiveCounter = 0,
    )
  }

  fun ratchetEncrypt(
    state: RatchetSessionState,
    plaintext: ByteArray,
  ): Pair<RatchetSessionState, EncryptedRatchetMessage> {
    val currentSendingCk = state.sendingChainKey
      ?: error("Cannot encrypt before receiving initial handshake message")

    val (nextCk, messageKey) = stepChainKey(currentSendingCk)
    val localPubB64 = Base64.getEncoder().encodeToString(state.localDhKeyPair.public.encoded)

    val header = RatchetHeader(
      senderDhPublicKeyBase64 = localPubB64,
      sequenceNumber = state.sendCounter,
      previousChainLength = state.previousChainLength,
    )

    val associatedData = serializeHeader(header)
    val encrypted = CryptoPrimitives.encryptAesGcm(
      key = messageKey,
      plaintext = plaintext,
      associatedData = associatedData,
    )

    val updatedState = state.copy(
      sendingChainKey = nextCk,
      sendCounter = state.sendCounter + 1,
    )

    val message = EncryptedRatchetMessage(
      header = header,
      ivBase64 = Base64.getEncoder().encodeToString(encrypted.iv),
      ciphertextWithTagBase64 = Base64.getEncoder().encodeToString(encrypted.ciphertextWithTag),
    )

    return Pair(updatedState, message)
  }

  fun ratchetDecrypt(
    state: RatchetSessionState,
    message: EncryptedRatchetMessage,
  ): Pair<RatchetSessionState, ByteArray> {
    val remotePubBytes = Base64.getDecoder().decode(message.header.senderDhPublicKeyBase64)
    val remotePublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(remotePubBytes))

    var currentState = state
    val isNewDhKey = state.remoteDhPublicKey == null || !state.remoteDhPublicKey.encoded.contentEquals(remotePublicKey.encoded)

    if (isNewDhKey) {
      // Step 1: DH ratchet with our current private key and their new public key
      val dhReceiveShared = CryptoPrimitives.computeSharedSecret(currentState.localDhKeyPair.private, remotePublicKey)
      val derivedReceive = CryptoPrimitives.hkdf(
        salt = currentState.rootKey,
        ikm = dhReceiveShared,
        info = KDF_RK_INFO,
        length = 64,
      )
      val newRootKey1 = derivedReceive.copyOfRange(0, 32)
      val newReceivingCk = derivedReceive.copyOfRange(32, 64)

      // Step 2: Generate fresh local DH pair for sending
      val newLocalKeyPair = CryptoPrimitives.generateEcdhKeyPair()
      val dhSendShared = CryptoPrimitives.computeSharedSecret(newLocalKeyPair.private, remotePublicKey)
      val derivedSend = CryptoPrimitives.hkdf(
        salt = newRootKey1,
        ikm = dhSendShared,
        info = KDF_RK_INFO,
        length = 64,
      )
      val newRootKey2 = derivedSend.copyOfRange(0, 32)
      val newSendingCk = derivedSend.copyOfRange(32, 64)

      currentState = currentState.copy(
        rootKey = newRootKey2,
        receivingChainKey = newReceivingCk,
        sendingChainKey = newSendingCk,
        localDhKeyPair = newLocalKeyPair,
        remoteDhPublicKey = remotePublicKey,
        previousChainLength = currentState.sendCounter,
        sendCounter = 0,
        receiveCounter = 0,
      )
    }

    val currentRecvCk = currentState.receivingChainKey
      ?: error("Receiving chain key is not initialized")

    val (nextRecvCk, messageKey) = stepChainKey(currentRecvCk)
    val associatedData = serializeHeader(message.header)
    val iv = Base64.getDecoder().decode(message.ivBase64)
    val ciphertext = Base64.getDecoder().decode(message.ciphertextWithTagBase64)

    val plaintext = CryptoPrimitives.decryptAesGcm(
      key = messageKey,
      iv = iv,
      ciphertextWithTag = ciphertext,
      associatedData = associatedData,
    )

    val finalState = currentState.copy(
      receivingChainKey = nextRecvCk,
      receiveCounter = currentState.receiveCounter + 1,
    )

    return Pair(finalState, plaintext)
  }

  private fun stepChainKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
    val messageKey = CryptoPrimitives.hkdf(
      salt = chainKey,
      ikm = MESSAGE_KEY_SEED,
      info = "PeykrMessageKey".toByteArray(),
      length = 32,
    )
    val nextChainKey = CryptoPrimitives.hkdf(
      salt = chainKey,
      ikm = CHAIN_KEY_SEED,
      info = "PeykrChainKey".toByteArray(),
      length = 32,
    )
    return Pair(nextChainKey, messageKey)
  }

  private fun serializeHeader(header: RatchetHeader): ByteArray {
    return "${header.senderDhPublicKeyBase64}:${header.sequenceNumber}:${header.previousChainLength}".toByteArray()
  }

  companion object {
    private val KDF_RK_INFO = "PeykrDoubleRatchetRK".toByteArray()
    private val MESSAGE_KEY_SEED = byteArrayOf(0x01)
    private val CHAIN_KEY_SEED = byteArrayOf(0x02)
  }
}

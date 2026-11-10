package com.peykr.app.domain.messaging

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.getOrNull
import com.peykr.app.core.security.CryptoPrimitives
import com.peykr.app.core.security.DoubleRatchetEngine
import com.peykr.app.core.security.EncryptedRatchetMessage
import com.peykr.app.core.security.RatchetSessionState
import com.peykr.app.core.security.RatchetStore
import com.peykr.app.domain.contacts.TrustedContact
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessagingEngine(
  private val deviceIdentity: DeviceIdentity,
  private val messagingRepository: MessagingRepository,
  private val ratchetEngine: DoubleRatchetEngine = DoubleRatchetEngine(),
  private val ratchetStore: RatchetStore? = null,
) {

  private val activeRatchets = ConcurrentHashMap<String, RatchetSessionState>()

  fun getOrInitRatchet(
    contactId: String,
    remotePublicKeyBase64: String,
    isInitiator: Boolean = true,
  ): RatchetSessionState {
    val existing = activeRatchets[contactId] ?: ratchetStore?.loadSession(contactId)
    if (existing != null) {
      activeRatchets[contactId] = existing
      return existing
    }

    val state = activeRatchets.computeIfAbsent(contactId) {
      val localInfo = deviceIdentity.getOrCreate().getOrNull()
      val localId = localInfo?.id ?: "local"
      val localPubKey = localInfo?.publicKey ?: ""

      val isLocalFirst = localId.compareTo(contactId) <= 0
      val canonicalPair = if (isLocalFirst) "$localId:$contactId" else "$contactId:$localId"
      val canonicalKeys = if (isLocalFirst) localPubKey + remotePublicKeyBase64 else remotePublicKeyBase64 + localPubKey

      val masterSecret = CryptoPrimitives.hkdf(
        salt = canonicalPair.toByteArray(StandardCharsets.UTF_8),
        ikm = canonicalKeys.toByteArray(StandardCharsets.UTF_8),
        info = "PeykrRatchetMasterV1".toByteArray(StandardCharsets.UTF_8),
        length = 32,
      )

      val sendInfo = if (isLocalFirst) "PeykrInit_1to2" else "PeykrInit_2to1"
      val recvInfo = if (isLocalFirst) "PeykrInit_2to1" else "PeykrInit_1to2"

      if (isInitiator) {
        ratchetEngine.initializeInitiatorWithSharedSecret(
          contactId = contactId,
          sharedMasterSecret = masterSecret,
          sendingChainInfo = sendInfo,
          receivingChainInfo = recvInfo,
        )
      } else {
        ratchetEngine.initializeResponderWithSharedSecret(
          contactId = contactId,
          sharedMasterSecret = masterSecret,
          sendingChainInfo = sendInfo,
          receivingChainInfo = recvInfo,
        )
      }
    }
    ratchetStore?.saveSession(contactId, state)
    return state
  }

  suspend fun createOneToOneConversation(contact: TrustedContact): Conversation {
    val conv = Conversation(
      id = "conv_${contact.id}",
      title = contact.displayName,
      isGroup = false,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
    )
    messagingRepository.saveConversation(conv)
    return conv
  }

  suspend fun createGroupConversation(title: String, memberContactIds: List<String>): Conversation {
    val localId = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val convId = "group_${UUID.randomUUID()}"
    val conv = Conversation(
      id = convId,
      title = title,
      isGroup = true,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis(),
    )
    messagingRepository.saveConversation(conv)
    messagingRepository.addGroupMember(GroupMember(convId, localId, role = "ADMIN"))
    for (m in memberContactIds) {
      messagingRepository.addGroupMember(GroupMember(convId, m, role = "MEMBER"))
    }
    return conv
  }

  suspend fun sendTextMessage(
    conversationId: String,
    recipientId: String,
    text: String,
    recipientPublicKeyBase64: String? = null,
    messageId: String? = null,
  ): Result<Pair<Message, EncryptedRatchetMessage?>> = runCatching {
    val localId = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val msgId = messageId ?: UUID.randomUUID().toString()

    val encryptedMessage = if (recipientPublicKeyBase64 != null) {
      val state = getOrInitRatchet(recipientId, recipientPublicKeyBase64, isInitiator = true)
      val (newState, encMsg) = ratchetEngine.ratchetEncrypt(state, text.toByteArray(StandardCharsets.UTF_8))
      activeRatchets[recipientId] = newState
      ratchetStore?.saveSession(recipientId, newState)
      encMsg
    } else null

    val message = Message(
      id = msgId,
      conversationId = conversationId,
      senderId = localId,
      recipientId = recipientId,
      textContent = text,
      status = MessageStatus.SENT,
      direction = MessageDirection.OUTGOING,
      timestamp = System.currentTimeMillis(),
    )

    messagingRepository.saveMessage(message)
    Pair(message, encryptedMessage)
  }

  suspend fun receiveEncryptedTextMessage(
    conversationId: String,
    senderId: String,
    encryptedMessage: EncryptedRatchetMessage,
    senderPublicKeyBase64: String,
    messageId: String? = null,
  ): Result<Message> = runCatching {
    val localId = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val state = getOrInitRatchet(senderId, senderPublicKeyBase64, isInitiator = false)
    val (newState, plaintextBytes) = ratchetEngine.ratchetDecrypt(state, encryptedMessage)
    activeRatchets[senderId] = newState
    ratchetStore?.saveSession(senderId, newState)

    val text = String(plaintextBytes, StandardCharsets.UTF_8)
    val msgId = messageId ?: UUID.randomUUID().toString()

    val message = Message(
      id = msgId,
      conversationId = conversationId,
      senderId = senderId,
      recipientId = localId,
      textContent = text,
      status = MessageStatus.DELIVERED,
      direction = MessageDirection.INCOMING,
      timestamp = System.currentTimeMillis(),
      verifiedDeliveryEpochMillis = System.currentTimeMillis(),
      transportType = MessageTransportType.LOCAL_WIFI,
      isRead = false,
    )

    messagingRepository.saveMessage(message)
    message
  }
}

package com.peykr.app.domain.messaging

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.getOrNull
import com.peykr.app.core.security.CryptoPrimitives
import com.peykr.app.core.security.DoubleRatchetEngine
import com.peykr.app.core.security.EncryptedRatchetMessage
import com.peykr.app.core.security.RatchetSessionState
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
) {

  private val activeRatchets = ConcurrentHashMap<String, RatchetSessionState>()

  fun getOrInitRatchet(contactId: String, remotePublicKeyBase64: String): RatchetSessionState {
    return activeRatchets.computeIfAbsent(contactId) {
      val remoteBytes = Base64.getDecoder().decode(remotePublicKeyBase64)
      val remotePubKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(remoteBytes))
      // Derive initial root seed from canonical contact ID digest
      val seed = CryptoPrimitives.hkdf(
        salt = contactId.toByteArray(),
        ikm = remoteBytes,
        info = "PeykrRatchetInit".toByteArray(),
        length = 32,
      )
      ratchetEngine.initializeInitiator(contactId, seed, remotePubKey)
    }
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
  ): Result<Pair<Message, EncryptedRatchetMessage?>> = runCatching {
    val localId = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val msgId = UUID.randomUUID().toString()

    val encryptedMessage = if (recipientPublicKeyBase64 != null) {
      val state = getOrInitRatchet(recipientId, recipientPublicKeyBase64)
      val (newState, encMsg) = ratchetEngine.ratchetEncrypt(state, text.toByteArray(StandardCharsets.UTF_8))
      activeRatchets[recipientId] = newState
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
  ): Result<Message> = runCatching {
    val localId = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val state = getOrInitRatchet(senderId, senderPublicKeyBase64)
    val (newState, plaintextBytes) = ratchetEngine.ratchetDecrypt(state, encryptedMessage)
    activeRatchets[senderId] = newState

    val text = String(plaintextBytes, StandardCharsets.UTF_8)
    val msgId = UUID.randomUUID().toString()

    val message = Message(
      id = msgId,
      conversationId = conversationId,
      senderId = senderId,
      recipientId = localId,
      textContent = text,
      status = MessageStatus.DELIVERED,
      direction = MessageDirection.INCOMING,
      timestamp = System.currentTimeMillis(),
      isRead = false,
    )

    messagingRepository.saveMessage(message)
    message
  }
}

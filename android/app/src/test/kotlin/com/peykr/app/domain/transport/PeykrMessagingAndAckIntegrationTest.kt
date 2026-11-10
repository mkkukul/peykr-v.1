package com.peykr.app.domain.transport

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.core.security.CryptoPrimitives
import com.peykr.app.core.security.DoubleRatchetEngine
import com.peykr.app.core.security.InMemoryRatchetStore
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.localsend.DirectSendAck
import com.peykr.app.domain.localsend.LocalHttpClient
import com.peykr.app.domain.localsend.LocalNetworkDiscovery
import com.peykr.app.domain.mesh.BleMeshEngine
import com.peykr.app.domain.mesh.MeshPacket
import com.peykr.app.domain.mesh.MeshPacketTransmitter
import com.peykr.app.domain.messaging.Conversation
import com.peykr.app.domain.messaging.GroupMember
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessageDirection
import com.peykr.app.domain.messaging.MessageStatus
import com.peykr.app.domain.messaging.MessageTransportType
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.domain.transfer.DiscoveredDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.util.Base64
import java.util.UUID

private class TestIdentityWithCrypto(
  val id: String,
  val keyPair: KeyPair = CryptoPrimitives.generateEcdhKeyPair(),
) : DeviceIdentity {
  val publicKeyBase64: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

  override val identityState: StateFlow<DeviceIdentityState> =
    MutableStateFlow(DeviceIdentityState.Ready(DeviceIdentityInfo(id, publicKeyBase64)))

  override fun initialize() {}
  override fun retry() {}
  override fun getOrCreate(): DeviceIdentityResult =
    DeviceIdentityResult.Success(DeviceIdentityInfo(id, publicKeyBase64))

  override fun sign(transcript: ByteArray): Result<ByteArray> = runCatching {
    CryptoPrimitives.signData(keyPair.private, transcript)
  }

  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean {
    return CryptoPrimitives.verifySignature(publicKeyBase64, transcript, signature)
  }

  override fun verify(publicKey: java.security.PublicKey, transcript: ByteArray, signature: ByteArray): Boolean {
    return CryptoPrimitives.verifySignature(publicKey, transcript, signature)
  }

  override fun close() {}
}

private class InMemoryContactRepo(initialContacts: List<TrustedContact> = emptyList()) : TrustedContactRepository {
  private val list = initialContacts.toMutableList()
  private val _flow = MutableStateFlow<List<TrustedContact>>(list.toList())
  override val contacts: Flow<List<TrustedContact>> = _flow.asStateFlow()

  override suspend fun getById(id: String): TrustedContact? = list.firstOrNull { it.id == id }
  override suspend fun add(contact: TrustedContact) {
    list.removeAll { it.id == contact.id }
    list.add(contact)
    _flow.value = list.toList()
  }
  override suspend fun update(contact: TrustedContact) {
    val idx = list.indexOfFirst { it.id == contact.id }
    if (idx >= 0) {
      list[idx] = contact
      _flow.value = list.toList()
    }
  }
  override suspend fun delete(id: String) {
    list.removeAll { it.id == id }
    _flow.value = list.toList()
  }
}

private class InMemoryMessagingRepo : MessagingRepository {
  val messages = mutableListOf<Message>()
  val conversations = mutableListOf<Conversation>()
  private val _messagesFlow = MutableStateFlow<List<Message>>(emptyList())

  override fun getConversations(): Flow<List<Conversation>> = MutableStateFlow(conversations.toList())
  override fun getMessages(conversationId: String): Flow<List<Message>> = _messagesFlow

  override suspend fun saveConversation(conversation: Conversation) {
    conversations.removeAll { it.id == conversation.id }
    conversations.add(conversation)
  }

  override suspend fun saveMessage(message: Message) {
    // Idempotent: replace by primary key 'id'
    val existingIndex = messages.indexOfFirst { it.id == message.id }
    if (existingIndex >= 0) {
      messages[existingIndex] = message
    } else {
      messages.add(message)
    }
    _messagesFlow.value = messages.toList()
  }

  override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
    val idx = messages.indexOfFirst { it.id == messageId }
    if (idx >= 0) {
      messages[idx] = messages[idx].copy(status = status)
      _messagesFlow.value = messages.toList()
    }
  }

  override suspend fun updateMessageDelivery(
    messageId: String,
    status: MessageStatus,
    transportType: MessageTransportType,
    deliveryTime: Long?
  ) {
    val idx = messages.indexOfFirst { it.id == messageId }
    if (idx >= 0) {
      messages[idx] = messages[idx].copy(
        status = status,
        transportType = transportType,
        verifiedDeliveryEpochMillis = deliveryTime
      )
      _messagesFlow.value = messages.toList()
    }
  }

  override suspend fun deleteMessage(messageId: String) {
    messages.removeAll { it.id == messageId }
  }

  override suspend fun deleteConversation(conversationId: String) {
    conversations.removeAll { it.id == conversationId }
    messages.removeAll { it.conversationId == conversationId }
  }

  override suspend fun getGroupMembers(conversationId: String): List<GroupMember> = emptyList()
  override suspend fun addGroupMember(member: GroupMember) {}
  override suspend fun removeGroupMember(conversationId: String, contactId: String) {}
}

class PeykrMessagingAndAckIntegrationTest {

  @Test
  fun testSenderEncryptReceiverDecryptAndSignedDeliveryAck() = runTest {
    // Device A (Alice) and Device B (Bob) setup
    val aliceIdentity = TestIdentityWithCrypto("alice-phone-001")
    val bobIdentity = TestIdentityWithCrypto("bob-phone-002")

    val aliceRepo = InMemoryMessagingRepo()
    val bobRepo = InMemoryMessagingRepo()

    val aliceStore = InMemoryRatchetStore()
    val bobStore = InMemoryRatchetStore()

    val aliceEngine = MessagingEngine(
      deviceIdentity = aliceIdentity,
      messagingRepository = aliceRepo,
      ratchetStore = aliceStore,
    )
    val bobEngine = MessagingEngine(
      deviceIdentity = bobIdentity,
      messagingRepository = bobRepo,
      ratchetStore = bobStore,
    )

    // Mutual trusted pairing
    val aliceContactBob = TrustedContact(
      id = bobIdentity.id,
      displayName = "Bob",
      publicIdentity = bobIdentity.publicKeyBase64,
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 1000L,
    )
    val bobContactAlice = TrustedContact(
      id = aliceIdentity.id,
      displayName = "Alice",
      publicIdentity = aliceIdentity.publicKeyBase64,
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 1000L,
    )

    val aliceContactRepo = InMemoryContactRepo(listOf(aliceContactBob))
    val bobContactRepo = InMemoryContactRepo(listOf(bobContactAlice))

    // 1. Alice sends encrypted message
    val wireMessageText = "Güvenli test mesajı: Alice'ten Bob'a"
    val sendResult = aliceEngine.sendTextMessage(
      conversationId = "conv_${bobIdentity.id}",
      recipientId = bobIdentity.id,
      text = wireMessageText,
      recipientPublicKeyBase64 = bobIdentity.publicKeyBase64,
    )
    assertTrue("Alice encryption must succeed", sendResult.isSuccess)
    val (aliceMessage, encryptedRatchetMessage) = sendResult.getOrThrow()
    assertNotNull("Encrypted payload must be present", encryptedRatchetMessage)

    // 2. Bob receives message on wire
    val bobReceiveResult = bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_${aliceIdentity.id}",
      senderId = aliceIdentity.id,
      encryptedMessage = encryptedRatchetMessage!!,
      senderPublicKeyBase64 = aliceIdentity.publicKeyBase64,
      messageId = aliceMessage.id, // wire ID
    )
    assertTrue("Bob decryption must succeed: ${bobReceiveResult.exceptionOrNull()?.message}", bobReceiveResult.isSuccess)
    val bobMessage = bobReceiveResult.getOrThrow()

    // Verify Bob received identical text content and retained wire ID
    assertEquals(wireMessageText, bobMessage.textContent)
    assertEquals(aliceMessage.id, bobMessage.id)
    assertEquals("conv_${aliceIdentity.id}", bobMessage.conversationId)
    assertEquals(MessageStatus.DELIVERED, bobMessage.status)

    // 3. Bob computes cryptographic delivery ACK
    val ackTimestamp = System.currentTimeMillis()
    val ackPayload = "${bobMessage.id}:DELIVERED:$ackTimestamp"
    val ackSignatureBytes = bobIdentity.sign(ackPayload.toByteArray(StandardCharsets.UTF_8)).getOrThrow()
    val ackSignatureBase64 = Base64.getEncoder().encodeToString(ackSignatureBytes)

    // 4. Alice verifies Bob's cryptographic ACK signature
    val isVerified = CryptoPrimitives.verifySignature(
      publicKeyBase64 = bobIdentity.publicKeyBase64,
      data = ackPayload.toByteArray(StandardCharsets.UTF_8),
      signature = Base64.getDecoder().decode(ackSignatureBase64),
    )
    assertTrue("Alice must cryptographically verify Bob's delivery signature", isVerified)

    // Alice updates status to DELIVERED with timestamp
    aliceRepo.updateMessageDelivery(
      messageId = aliceMessage.id,
      status = MessageStatus.DELIVERED,
      transportType = MessageTransportType.LOCAL_WIFI,
      deliveryTime = ackTimestamp,
    )

    val aliceSavedMessage = aliceRepo.messages.first { it.id == aliceMessage.id }
    assertEquals(MessageStatus.DELIVERED, aliceSavedMessage.status)
    assertEquals(ackTimestamp, aliceSavedMessage.verifiedDeliveryEpochMillis)

    // Ratchet state must be persisted in stores
    assertNotNull("Alice ratchet state must be stored", aliceStore.loadSession(bobIdentity.id))
    assertNotNull("Bob ratchet state must be stored", bobStore.loadSession(aliceIdentity.id))
  }

  @Test
  fun testExactlyOncePersistenceAndWireMessageIdDeduplication() = runTest {
    val aliceIdentity = TestIdentityWithCrypto("alice-phone-001")
    val bobIdentity = TestIdentityWithCrypto("bob-phone-002")

    val bobRepo = InMemoryMessagingRepo()
    val bobEngine = MessagingEngine(
      deviceIdentity = bobIdentity,
      messagingRepository = bobRepo,
      ratchetStore = InMemoryRatchetStore(),
    )

    val wireId = "unique-wire-msg-${UUID.randomUUID()}"
    val testMsg = Message(
      id = wireId,
      conversationId = "conv_${aliceIdentity.id}",
      senderId = aliceIdentity.id,
      recipientId = bobIdentity.id,
      textContent = "Deduplication check",
      status = MessageStatus.DELIVERED,
      direction = MessageDirection.INCOMING,
      timestamp = 1000L,
    )

    // First arrival
    bobRepo.saveMessage(testMsg)
    assertEquals(1, bobRepo.messages.size)

    // Retransmission of identical wire ID (e.g. ACK dropped on Wi-Fi)
    val retransmission = testMsg.copy(timestamp = 1005L)
    bobRepo.saveMessage(retransmission)

    // Must still contain exactly 1 message (no duplicate bubbles)
    assertEquals("Messages must be deduplicated by wire ID", 1, bobRepo.messages.size)
    assertEquals(wireId, bobRepo.messages[0].id)
  }

  @Test
  fun testDoubleRatchetReplayProtectionRejectsStaleSequence() = runTest {
    val aliceIdentity = TestIdentityWithCrypto("alice-phone-001")
    val bobIdentity = TestIdentityWithCrypto("bob-phone-002")

    val aliceRepo = InMemoryMessagingRepo()
    val bobRepo = InMemoryMessagingRepo()

    val aliceEngine = MessagingEngine(deviceIdentity = aliceIdentity, messagingRepository = aliceRepo)
    val bobEngine = MessagingEngine(deviceIdentity = bobIdentity, messagingRepository = bobRepo)

    // Message 1
    val (msg1, enc1) = aliceEngine.sendTextMessage(
      conversationId = "conv_${bobIdentity.id}",
      recipientId = bobIdentity.id,
      text = "Packet 1",
      recipientPublicKeyBase64 = bobIdentity.publicKeyBase64,
    ).getOrThrow()

    // Message 2
    val (msg2, enc2) = aliceEngine.sendTextMessage(
      conversationId = "conv_${bobIdentity.id}",
      recipientId = bobIdentity.id,
      text = "Packet 2",
      recipientPublicKeyBase64 = bobIdentity.publicKeyBase64,
    ).getOrThrow()

    // Bob processes Message 1 then Message 2
    bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_${aliceIdentity.id}",
      senderId = aliceIdentity.id,
      encryptedMessage = enc1!!,
      senderPublicKeyBase64 = aliceIdentity.publicKeyBase64,
      messageId = msg1.id,
    ).getOrThrow()

    bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_${aliceIdentity.id}",
      senderId = aliceIdentity.id,
      encryptedMessage = enc2!!,
      senderPublicKeyBase64 = aliceIdentity.publicKeyBase64,
      messageId = msg2.id,
    ).getOrThrow()

    // Attacker or stale network delivers Message 1 again (replay)
    val replayResult = bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_${aliceIdentity.id}",
      senderId = aliceIdentity.id,
      encryptedMessage = enc1,
      senderPublicKeyBase64 = aliceIdentity.publicKeyBase64,
      messageId = "replayed-id",
    )

    assertTrue("Replayed message must fail decryption", replayResult.isFailure)
    val exception = replayResult.exceptionOrNull()
    assertTrue(
      "Failure must be SecurityException for replay attack",
      exception is SecurityException && exception.message?.contains("Replay attack detected") == true
    )
  }

  @Test
  fun testOfflineQueueDrainsAndAcknowledgesRemovalUponSignedReceipt() = runTest {
    val aliceIdentity = TestIdentityWithCrypto("alice-phone-001")
    val bobIdentity = TestIdentityWithCrypto("bob-phone-002")

    val aliceRepo = InMemoryMessagingRepo()
    val bobRepo = InMemoryMessagingRepo()

    val aliceEngine = MessagingEngine(deviceIdentity = aliceIdentity, messagingRepository = aliceRepo)
    val bleMeshEngine = BleMeshEngine(localDeviceId = aliceIdentity.id, scope = this)

    val bobContact = TrustedContact(
      id = bobIdentity.id,
      displayName = "Bob",
      publicIdentity = bobIdentity.publicKeyBase64,
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 1000L,
    )
    val contactRepo = InMemoryContactRepo(listOf(bobContact))

    // Discovered devices initially empty
    val discovery = LocalNetworkDiscovery(
      context = null,
      localDeviceId = aliceIdentity.id,
      localDisplayName = "Alice",
      localPort = 53317,
      localFingerprint = aliceIdentity.publicKeyBase64,
    )

    val client = LocalHttpClient(localDeviceId = aliceIdentity.id, localDisplayName = "Alice")

    val transportManager = TransportManager(
      localDeviceId = aliceIdentity.id,
      localDisplayName = "Alice",
      messagingRepository = aliceRepo,
      messagingEngine = aliceEngine,
      bleMeshEngine = bleMeshEngine,
      localNetworkDiscovery = discovery,
      localHttpClient = client,
      contactRepository = contactRepo,
      scope = backgroundScope,
    )

    // 1. Send message when Bob is completely offline
    val result = transportManager.sendReliableMessage(
      conversationId = "conv_${bobIdentity.id}",
      recipientDeviceId = bobIdentity.id,
      text = "Kuyruk mesajı (çevrimdışı)",
    )

    assertEquals(MessageStatus.QUEUED_OFFLINE, result.status)
    assertEquals(MessageTransportType.OFFLINE_QUEUED, result.transportType)
    assertNull(result.verifiedDeliveryEpochMillis)
    assertEquals(1, bleMeshEngine.offlineQueue.getPendingCount())

    // 2. Simulate Bob receiving and signing ACK
    val queuedPackets = bleMeshEngine.offlineQueue.drainForPeer(bobIdentity.id)
    assertEquals(1, queuedPackets.size)
    val packet = queuedPackets[0]

    val ackTimestamp = System.currentTimeMillis()
    val ackPayload = "${packet.packetId}:DELIVERED:$ackTimestamp"
    val sig = bobIdentity.sign(ackPayload.toByteArray(StandardCharsets.UTF_8)).getOrThrow()

    // 3. Verify signature and acknowledge removal
    val sigVerified = CryptoPrimitives.verifySignature(
      publicKeyBase64 = bobIdentity.publicKeyBase64,
      data = ackPayload.toByteArray(StandardCharsets.UTF_8),
      signature = sig,
    )
    assertTrue("ACK signature must be verified", sigVerified)

    bleMeshEngine.offlineQueue.acknowledgeDelivery(packet.packetId)
    aliceRepo.updateMessageDelivery(
      messageId = packet.packetId,
      status = MessageStatus.DELIVERED,
      transportType = MessageTransportType.LOCAL_WIFI,
      deliveryTime = ackTimestamp,
    )

    // Queue must now be empty and status DELIVERED
    assertEquals("Offline queue must be drained", 0, bleMeshEngine.offlineQueue.getPendingCount())
    val finalSaved = aliceRepo.messages.first { it.id == packet.packetId }
    assertEquals(MessageStatus.DELIVERED, finalSaved.status)
    assertEquals(ackTimestamp, finalSaved.verifiedDeliveryEpochMillis)
  }

  @Test
  fun testSosLocationDeliveryRequiresVerifiedDeliveryAck() = runTest {
    val aliceIdentity = TestIdentityWithCrypto("alice-phone-001")
    val bobIdentity = TestIdentityWithCrypto("bob-phone-002")

    val aliceRepo = InMemoryMessagingRepo()
    val aliceEngine = MessagingEngine(deviceIdentity = aliceIdentity, messagingRepository = aliceRepo)
    val meshEngine = BleMeshEngine(localDeviceId = aliceIdentity.id, scope = this)

    val bobContact = TrustedContact(
      id = bobIdentity.id,
      displayName = "Bob",
      publicIdentity = bobIdentity.publicKeyBase64,
      pairingStatus = PairingStatus.PAIRED,
      createdAtEpochMillis = 1000L,
    )
    val contactRepo = InMemoryContactRepo(listOf(bobContact))

    val discovery = LocalNetworkDiscovery(
      context = null,
      localDeviceId = aliceIdentity.id,
      localDisplayName = "Alice",
      localPort = 53317,
      localFingerprint = aliceIdentity.publicKeyBase64,
    )
    val client = LocalHttpClient(localDeviceId = aliceIdentity.id, localDisplayName = "Alice")

    val transportManager = TransportManager(
      localDeviceId = aliceIdentity.id,
      localDisplayName = "Alice",
      messagingRepository = aliceRepo,
      messagingEngine = aliceEngine,
      bleMeshEngine = meshEngine,
      localNetworkDiscovery = discovery,
      localHttpClient = client,
      contactRepository = contactRepo,
      scope = backgroundScope,
    )

    // Scenario 1: Bob is offline -> SOS message must be queued and NOT marked DELIVERED
    val sosText = "🚨 ACİL DURUM (SOS): Canlı konumum https://maps.google.com/?q=41.0082,28.9784"
    val resOffline = transportManager.sendReliableMessage(
      conversationId = "conv_${bobIdentity.id}",
      recipientDeviceId = bobIdentity.id,
      text = sosText,
      recipientPublicKeyBase64 = bobIdentity.publicKeyBase64,
    )

    assertEquals(MessageStatus.QUEUED_OFFLINE, resOffline.status)
    assertNull("Unreached peer must NOT have verified delivery time", resOffline.verifiedDeliveryEpochMillis)

    // Scenario 2: Simulated delivery confirmation with signed ACK
    val ackTimestamp = System.currentTimeMillis()
    val ackPayload = "${resOffline.messageId}:DELIVERED:$ackTimestamp"
    val ackSig = bobIdentity.sign(ackPayload.toByteArray(StandardCharsets.UTF_8)).getOrThrow()

    val validSig = CryptoPrimitives.verifySignature(
      publicKeyBase64 = bobIdentity.publicKeyBase64,
      data = ackPayload.toByteArray(StandardCharsets.UTF_8),
      signature = ackSig,
    )
    assertTrue("Signature must verify", validSig)

    aliceRepo.updateMessageDelivery(
      messageId = resOffline.messageId,
      status = MessageStatus.DELIVERED,
      transportType = MessageTransportType.LOCAL_WIFI,
      deliveryTime = ackTimestamp,
    )

    val updatedSos = aliceRepo.messages.first { it.id == resOffline.messageId }
    assertEquals(MessageStatus.DELIVERED, updatedSos.status)
    assertNotNull(updatedSos.verifiedDeliveryEpochMillis)
  }
}

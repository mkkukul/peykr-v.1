package com.peykr.app.domain.transport

import android.content.Context
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.domain.localsend.LocalHttpClient
import com.peykr.app.domain.localsend.LocalNetworkDiscovery
import com.peykr.app.domain.mesh.BleMeshEngine
import com.peykr.app.domain.mesh.MeshPacketTransmitter
import com.peykr.app.domain.mesh.MeshPacket
import com.peykr.app.domain.messaging.Conversation
import com.peykr.app.domain.messaging.GroupMember
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessageStatus
import com.peykr.app.domain.messaging.MessageTransportType
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.domain.transfer.DiscoveredDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator

class FakeMessagingRepository : MessagingRepository {
  val savedMessages = mutableListOf<Message>()
  private val flow = MutableStateFlow<List<Message>>(emptyList())

  override fun getConversations(): Flow<List<Conversation>> = MutableStateFlow(emptyList())
  override fun getMessages(conversationId: String): Flow<List<Message>> = flow
  override suspend fun saveConversation(conversation: Conversation) {}
  override suspend fun saveMessage(message: Message) {
    savedMessages.add(message)
    flow.value = savedMessages.toList()
  }
  override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {
    val idx = savedMessages.indexOfFirst { it.id == messageId }
    if (idx >= 0) savedMessages[idx] = savedMessages[idx].copy(status = status)
  }
  override suspend fun updateMessageDelivery(
    messageId: String,
    status: MessageStatus,
    transportType: MessageTransportType,
    deliveryTime: Long?
  ) {
    val idx = savedMessages.indexOfFirst { it.id == messageId }
    if (idx >= 0) {
      savedMessages[idx] = savedMessages[idx].copy(
        status = status,
        transportType = transportType,
        verifiedDeliveryEpochMillis = deliveryTime
      )
    }
  }
  override suspend fun deleteMessage(messageId: String) {}
  override suspend fun deleteConversation(conversationId: String) {}
  override suspend fun getGroupMembers(conversationId: String): List<GroupMember> = emptyList()
  override suspend fun addGroupMember(member: GroupMember) {}
  override suspend fun removeGroupMember(conversationId: String, contactId: String) {}
}

class FakeDeviceIdentity(val id: String = "local-device") : DeviceIdentity {
  override val identityState: StateFlow<com.peykr.app.core.identity.DeviceIdentityState> =
    MutableStateFlow(com.peykr.app.core.identity.DeviceIdentityState.Ready(com.peykr.app.core.identity.DeviceIdentityInfo(id, "pk")))
  override fun initialize() {}
  override fun retry() {}
  override fun getOrCreate(): com.peykr.app.core.identity.DeviceIdentityResult =
    com.peykr.app.core.identity.DeviceIdentityResult.Success(com.peykr.app.core.identity.DeviceIdentityInfo(id, "pk"))
  override fun sign(transcript: ByteArray): Result<ByteArray> = Result.success(ByteArray(64))
  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun verify(publicKey: java.security.PublicKey, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun close() {}
}

class TransportManagerTest {

  @Test
  fun testOfflineQueuingWhenPeerIsUnreachable() = runTest {
    val fakeRepo = FakeMessagingRepository()
    val fakeIdentity = FakeDeviceIdentity()
    val msgEngine = MessagingEngine(fakeIdentity, fakeRepo)
    val meshEngine = BleMeshEngine(localDeviceId = "local-device", scope = this)

    // Neither Wi-Fi nor BLE peers are available
    val discovery = LocalNetworkDiscovery(
      context = null,
      localDeviceId = "local-device",
      localDisplayName = "Local",
      localPort = 53317,
      localFingerprint = "fp"
    )
    val client = LocalHttpClient(localDeviceId = "local-device", localDisplayName = "Local")

    val transportManager = TransportManager(
      localDeviceId = "local-device",
      localDisplayName = "Local",
      messagingRepository = fakeRepo,
      messagingEngine = msgEngine,
      bleMeshEngine = meshEngine,
      localNetworkDiscovery = discovery,
      localHttpClient = client,
    )

    val result = transportManager.sendReliableMessage(
      conversationId = "conv_unreachable",
      recipientDeviceId = "unreachable-peer",
      text = "Are you there?",
    )

    assertEquals(MessageStatus.QUEUED_OFFLINE, result.status)
    assertEquals(MessageTransportType.OFFLINE_QUEUED, result.transportType)
    assertNull("Unverified message must NOT have verified delivery time", result.verifiedDeliveryEpochMillis)
    assertEquals(1, meshEngine.offlineQueue.getPendingCount())
  }

  @Test
  fun testBleMeshDeliveryAndAckProgression() = runTest {
    val fakeRepo = FakeMessagingRepository()
    val fakeIdentity = FakeDeviceIdentity()
    val msgEngine = MessagingEngine(fakeIdentity, fakeRepo)
    val meshEngine = BleMeshEngine(localDeviceId = "local-device", scope = backgroundScope)

    meshEngine.transmitter = object : MeshPacketTransmitter {
      override fun transmitToPeer(peerId: String, packet: MeshPacket): Boolean = true
      override fun broadcastToAllConnectedPeers(packet: MeshPacket): Int = 1
    }

    meshEngine.registerPeer("mesh-peer-1")

    val discovery = LocalNetworkDiscovery(
      context = null,
      localDeviceId = "local-device",
      localDisplayName = "Local",
      localPort = 53317,
      localFingerprint = "fp"
    )
    val client = LocalHttpClient(localDeviceId = "local-device", localDisplayName = "Local")

    val transportManager = TransportManager(
      localDeviceId = "local-device",
      localDisplayName = "Local",
      messagingRepository = fakeRepo,
      messagingEngine = msgEngine,
      bleMeshEngine = meshEngine,
      localNetworkDiscovery = discovery,
      localHttpClient = client,
      scope = backgroundScope,
    )
    testScheduler.advanceUntilIdle()

    val result = transportManager.sendReliableMessage(
      conversationId = "conv_mesh",
      recipientDeviceId = "mesh-peer-1",
      text = "Direct over BLE mesh!",
    )

    assertEquals(MessageStatus.SENT, result.status)
    assertEquals(MessageTransportType.BLUETOOTH_MESH, result.transportType)

    // Simulate incoming delivery ACK from mesh peer
    val ackPacket = MeshPacket(
      packetId = "ack-1",
      senderDeviceId = "mesh-peer-1",
      recipientDeviceId = "local-device",
      payloadType = com.peykr.app.domain.mesh.MeshPayloadType.DELIVERY_ACK,
      payload = result.messageId.toByteArray(Charsets.UTF_8),
    )
    meshEngine.handleIncomingPacket(ackPacket, fromPeerId = "mesh-peer-1")
    testScheduler.advanceUntilIdle()

    // Verify repo updated status to DELIVERED
    val updated = fakeRepo.savedMessages.first { it.id == result.messageId }
    assertEquals(MessageStatus.DELIVERED, updated.status)
    assertEquals(MessageTransportType.BLUETOOTH_MESH, updated.transportType)
    assertNotNull(updated.verifiedDeliveryEpochMillis)
  }
}

package com.peykr.app.domain.transport

import android.util.Log
import com.peykr.app.domain.localsend.LocalHttpClient
import com.peykr.app.domain.localsend.LocalNetworkDiscovery
import com.peykr.app.domain.mesh.BleMeshEngine
import com.peykr.app.domain.mesh.MeshPacket
import com.peykr.app.domain.mesh.MeshPayloadType
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessageStatus
import com.peykr.app.domain.messaging.MessageTransportType
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.peykr.app.domain.contacts.TrustedContact
import java.nio.charset.StandardCharsets

data class VerifiedDeliveryResult(
  val messageId: String,
  val status: MessageStatus,
  val transportType: MessageTransportType,
  val verifiedDeliveryEpochMillis: Long? = null,
  val errorMessage: String? = null,
)

data class TransportConnectivityState(
  val isWifiActive: Boolean = false,
  val wifiPeersCount: Int = 0,
  val isBleMeshActive: Boolean = false,
  val bleMeshPeersCount: Int = 0,
  val offlineQueuedCount: Int = 0,
)

class TransportManager(
  val localDeviceId: String,
  val localDisplayName: String,
  val messagingRepository: MessagingRepository,
  val messagingEngine: MessagingEngine,
  val bleMeshEngine: BleMeshEngine,
  val localNetworkDiscovery: LocalNetworkDiscovery,
  val localHttpClient: LocalHttpClient,
  val contactRepository: com.peykr.app.domain.contacts.TrustedContactRepository? = null,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

  private val _connectivityState = MutableStateFlow(TransportConnectivityState())
  val connectivityState: StateFlow<TransportConnectivityState> = _connectivityState.asStateFlow()

  init {
    // Listen to mesh delivery acks to update database verified delivery timestamps
    scope.launch(Dispatchers.Unconfined) {
      bleMeshEngine.deliveryAcks.collect { ackedPacketId ->
        messagingRepository.updateMessageDelivery(
          messageId = ackedPacketId,
          status = MessageStatus.DELIVERED,
          transportType = MessageTransportType.BLUETOOTH_MESH,
          deliveryTime = System.currentTimeMillis()
        )
      }
    }

    // Monitor connectivity state changes and drain queued messages when Wi-Fi peers appear
    scope.launch(Dispatchers.Unconfined) {
      localNetworkDiscovery.discoveredDevices.collect { devices ->
        updateConnectivityState { it.copy(isWifiActive = true, wifiPeersCount = devices.size) }
        val allContacts = contactRepository?.let { repo ->
          runCatching { repo.contacts.first() }.getOrDefault(emptyList())
        } ?: emptyList()

        for (dev in devices) {
          val matchingContacts = allContacts.filter { c ->
            c.id == dev.deviceId || c.id.equals(dev.deviceId, ignoreCase = true) ||
              (c.publicIdentity != null && c.publicIdentity == dev.fingerprint) ||
              (c.id.length >= 8 && dev.deviceId.removePrefix("Peykr-").startsWith(c.id.take(8), ignoreCase = true))
          }

          val candidateIds = (listOf(dev.deviceId) + matchingContacts.map { it.id }).distinct()
          for (peerId in candidateIds) {
            val pending = bleMeshEngine.offlineQueue.drainForPeer(peerId)
            val contact = matchingContacts.firstOrNull { it.id == peerId } ?: contactRepository?.getById(peerId)
            val targetPubKey = contact?.publicIdentity ?: dev.fingerprint.takeIf { it.isNotBlank() }
            if (contact != null && contact.pairingStatus == com.peykr.app.domain.contacts.PairingStatus.PAIRED && targetPubKey != null) {
              for (packet in pending) {
                val text = String(packet.payload, StandardCharsets.UTF_8)
                val encResult = messagingEngine.sendTextMessage(
                  conversationId = "conv_${packet.recipientDeviceId}",
                  recipientId = packet.recipientDeviceId,
                  text = text,
                  recipientPublicKeyBase64 = targetPubKey,
                  messageId = packet.packetId,
                )
                if (encResult.isSuccess) {
                  val (retryMsg, encMsg) = encResult.getOrThrow()
                  val directResult = localHttpClient.sendDirectMessage(dev, retryMsg, encMsg)
                  if (directResult.isSuccess) {
                    val ack = directResult.getOrThrow()
                    val verified = if (ack.ackPayload != null && ack.ackSignature != null && targetPubKey != null) {
                      com.peykr.app.core.security.CryptoPrimitives.verifySignature(
                        publicKeyBase64 = targetPubKey,
                        data = ack.ackPayload.toByteArray(StandardCharsets.UTF_8),
                        signature = java.util.Base64.getDecoder().decode(ack.ackSignature)
                      )
                    } else ack.success

                    if (verified) {
                      bleMeshEngine.offlineQueue.acknowledgeDelivery(packet.packetId)
                      messagingRepository.updateMessageDelivery(
                        messageId = packet.packetId,
                        status = MessageStatus.DELIVERED,
                        transportType = MessageTransportType.LOCAL_WIFI,
                        deliveryTime = System.currentTimeMillis()
                      )
                    }
                  }
                }
              }
            }
          }
        }
        updateConnectivityState { it.copy(offlineQueuedCount = bleMeshEngine.offlineQueue.getPendingCount()) }
      }
    }

    // Monitor BLE mesh peers
    scope.launch(Dispatchers.Unconfined) {
      bleMeshEngine.meshPeers.collect { peers ->
        updateConnectivityState { it.copy(isBleMeshActive = true, bleMeshPeersCount = peers.size) }
      }
    }
  }

  suspend fun sendReliableMessage(
    conversationId: String,
    recipientDeviceId: String,
    text: String,
    recipientPublicKeyBase64: String? = null,
  ): VerifiedDeliveryResult {
    val localId = localDeviceId

    // 1. Channel or Broadcast Message (BitChat model)
    if (recipientDeviceId.startsWith("#") || recipientDeviceId == "*") {
      val packet = bleMeshEngine.sendPacket(
        recipientDeviceId = recipientDeviceId,
        payloadType = MeshPayloadType.CHANNEL_BROADCAST,
        payload = text.toByteArray(StandardCharsets.UTF_8),
      )
      val msg = Message(
        id = packet.packetId,
        conversationId = conversationId,
        senderId = localId,
        recipientId = recipientDeviceId,
        textContent = text,
        status = MessageStatus.SENT,
        transportType = MessageTransportType.BLUETOOTH_MESH,
        timestamp = packet.timestamp,
        verifiedDeliveryEpochMillis = packet.timestamp,
      )
      messagingRepository.saveMessage(msg)
      return VerifiedDeliveryResult(
        messageId = packet.packetId,
        status = MessageStatus.SENT,
        transportType = MessageTransportType.BLUETOOTH_MESH,
        verifiedDeliveryEpochMillis = packet.timestamp,
      )
    }

    val contact = contactRepository?.getById(recipientDeviceId)
      ?: runCatching {
        val list = contactRepository?.contacts?.first()
        list?.firstOrNull { it.id.equals(recipientDeviceId, ignoreCase = true) }
      }.getOrNull()

    val targetDeviceId = contact?.id ?: recipientDeviceId
    val targetFingerprint = contact?.publicIdentity

    // 2. Check Local Wi-Fi LAN availability (LocalSend model)
    var wifiTarget = localNetworkDiscovery.discoveredDevices.value.firstOrNull {
      it.deviceId == targetDeviceId ||
      it.deviceId == recipientDeviceId ||
      (targetFingerprint != null && it.fingerprint == targetFingerprint)
    }

    if (wifiTarget == null) {
      val cached = localNetworkDiscovery.getCachedPeerAddress(targetDeviceId)
        ?: localNetworkDiscovery.getCachedPeerAddress(recipientDeviceId)
      if (cached != null) {
        wifiTarget = localNetworkDiscovery.probeHost(cached.first, cached.second)
      }
    }

    if (wifiTarget != null) {
      if (contact != null && contact.pairingStatus != com.peykr.app.domain.contacts.PairingStatus.PAIRED) {
        Log.w(TAG, "Contact $recipientDeviceId is not in PAIRED status. Refusing unauthenticated transport.")
        return VerifiedDeliveryResult(
          messageId = java.util.UUID.randomUUID().toString(),
          status = MessageStatus.FAILED,
          transportType = MessageTransportType.LOCAL_WIFI,
          errorMessage = "Recipient is not an authenticated paired contact"
        )
      }

      val targetPubKey = contact?.publicIdentity ?: recipientPublicKeyBase64
      if (targetPubKey == null) {
        Log.w(TAG, "Recipient $recipientDeviceId has no public identity key. Refusing cleartext transmission.")
        return VerifiedDeliveryResult(
          messageId = java.util.UUID.randomUUID().toString(),
          status = MessageStatus.FAILED,
          transportType = MessageTransportType.LOCAL_WIFI,
          errorMessage = "Recipient has no public identity for end-to-end encryption"
        )
      }

      val encResult = messagingEngine.sendTextMessage(conversationId, recipientDeviceId, text, targetPubKey)
      if (encResult.isFailure) {
        val err = encResult.exceptionOrNull()?.message ?: "Encryption failure"
        return VerifiedDeliveryResult(
          messageId = java.util.UUID.randomUUID().toString(),
          status = MessageStatus.FAILED,
          transportType = MessageTransportType.LOCAL_WIFI,
          errorMessage = err
        )
      }

      val (msg, encMsg) = encResult.getOrThrow()
      val directResult = localHttpClient.sendDirectMessage(wifiTarget, msg, encMsg)
      if (directResult.isSuccess) {
        val ack = directResult.getOrThrow()
        val verified = if (ack.ackPayload != null && ack.ackSignature != null && targetPubKey != null) {
          com.peykr.app.core.security.CryptoPrimitives.verifySignature(
            publicKeyBase64 = targetPubKey,
            data = ack.ackPayload.toByteArray(StandardCharsets.UTF_8),
            signature = java.util.Base64.getDecoder().decode(ack.ackSignature)
          )
        } else ack.success

        if (verified) {
          val now = System.currentTimeMillis()
          messagingRepository.updateMessageDelivery(msg.id, MessageStatus.DELIVERED, MessageTransportType.LOCAL_WIFI, now)
          return VerifiedDeliveryResult(
            messageId = msg.id,
            status = MessageStatus.DELIVERED,
            transportType = MessageTransportType.LOCAL_WIFI,
            verifiedDeliveryEpochMillis = now,
          )
        } else {
          Log.w(TAG, "Cryptographic delivery acknowledgement verification failed from $recipientDeviceId")
        }
      }
    }

    // 3. Check Bluetooth Low Energy Mesh path (BitChat model)
    val isMeshPeer = bleMeshEngine.isPeerConnected(recipientDeviceId)
    if (isMeshPeer || bleMeshEngine.meshPeers.value.isNotEmpty()) {
      val packet = bleMeshEngine.sendPacket(
        recipientDeviceId = recipientDeviceId,
        payloadType = MeshPayloadType.TEXT_MESSAGE,
        payload = text.toByteArray(StandardCharsets.UTF_8),
      )
      val msg = Message(
        id = packet.packetId,
        conversationId = conversationId,
        senderId = localId,
        recipientId = recipientDeviceId,
        textContent = text,
        status = MessageStatus.SENT,
        transportType = MessageTransportType.BLUETOOTH_MESH,
        timestamp = packet.timestamp,
      )
      messagingRepository.saveMessage(msg)

      return VerifiedDeliveryResult(
        messageId = packet.packetId,
        status = MessageStatus.SENT,
        transportType = MessageTransportType.BLUETOOTH_MESH,
      )
    }

    // 4. Offline Store-and-Forward Queue (Peer unreachable)
    val packet = bleMeshEngine.sendPacket(
      recipientDeviceId = recipientDeviceId,
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = text.toByteArray(StandardCharsets.UTF_8),
    )
    val msg = Message(
      id = packet.packetId,
      conversationId = conversationId,
      senderId = localId,
      recipientId = recipientDeviceId,
      textContent = text,
      status = MessageStatus.QUEUED_OFFLINE,
      transportType = MessageTransportType.OFFLINE_QUEUED,
      timestamp = packet.timestamp,
    )
    messagingRepository.saveMessage(msg)
    updateConnectivityState { it.copy(offlineQueuedCount = bleMeshEngine.offlineQueue.getPendingCount()) }

    return VerifiedDeliveryResult(
      messageId = packet.packetId,
      status = MessageStatus.QUEUED_OFFLINE,
      transportType = MessageTransportType.OFFLINE_QUEUED,
      verifiedDeliveryEpochMillis = null,
    )
  }

  private fun updateConnectivityState(update: (TransportConnectivityState) -> TransportConnectivityState) {
    _connectivityState.value = update(_connectivityState.value)
  }

  companion object {
    private const val TAG = "TransportManager"
  }
}

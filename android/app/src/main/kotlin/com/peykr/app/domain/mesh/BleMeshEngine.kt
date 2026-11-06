package com.peykr.app.domain.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface MeshPacketTransmitter {
  fun transmitToPeer(peerId: String, packet: MeshPacket): Boolean
  fun broadcastToAllConnectedPeers(packet: MeshPacket): Int
}

class BleMeshEngine(
  val localDeviceId: String,
  val offlineQueue: OfflineMessageQueue = OfflineMessageQueue(),
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
  var transmitter: MeshPacketTransmitter? = null,
  private val maxSeenPacketsCache: Int = 2000,
) {

  // Deduplication cache to prevent broadcast storm and relay loops
  private val seenPacketIds: MutableSet<String> = Collections.synchronizedSet(
    object : LinkedHashMap<String, Boolean>(maxSeenPacketsCache, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
        return size > maxSeenPacketsCache
      }
    }.let { Collections.newSetFromMap(it) }
  )

  private val _meshPeers = MutableStateFlow<Set<String>>(emptySet())
  val meshPeers: StateFlow<Set<String>> = _meshPeers.asStateFlow()

  private val _incomingPackets = MutableSharedFlow<MeshPacket>(replay = 16, extraBufferCapacity = 64)
  val incomingPackets: SharedFlow<MeshPacket> = _incomingPackets.asSharedFlow()

  private val _channelMessages = MutableSharedFlow<Pair<String, MeshPacket>>(replay = 16, extraBufferCapacity = 64)
  val channelMessages: SharedFlow<Pair<String, MeshPacket>> = _channelMessages.asSharedFlow()

  private val _deliveryAcks = MutableSharedFlow<String>(replay = 16, extraBufferCapacity = 64)
  val deliveryAcks: SharedFlow<String> = _deliveryAcks.asSharedFlow()

  fun isPeerConnected(peerId: String): Boolean = _meshPeers.value.contains(peerId)

  fun registerPeer(peerId: String) {
    if (peerId == localDeviceId) return
    val current = _meshPeers.value.toMutableSet()
    if (current.add(peerId)) {
      _meshPeers.value = current
      // Drain offline queue for this newly encountered peer
      drainOfflineQueueForPeer(peerId)
    }
  }

  fun unregisterPeer(peerId: String) {
    val current = _meshPeers.value.toMutableSet()
    if (current.remove(peerId)) {
      _meshPeers.value = current
    }
  }

  fun sendPacket(
    recipientDeviceId: String,
    payloadType: MeshPayloadType,
    payload: ByteArray,
    signature: String = "",
  ): MeshPacket {
    val packet = MeshPacket(
      packetId = UUID.randomUUID().toString(),
      senderDeviceId = localDeviceId,
      recipientDeviceId = recipientDeviceId,
      ttl = MeshPacket.DEFAULT_TTL,
      hopCount = 0,
      payloadType = payloadType,
      payload = payload,
      signature = signature,
    )
    seenPacketIds.add(packet.packetId)

    val tx = transmitter
    if (tx != null) {
      if (recipientDeviceId.startsWith("#") || recipientDeviceId == "*") {
        tx.broadcastToAllConnectedPeers(packet)
      } else if (isPeerConnected(recipientDeviceId)) {
        val sent = tx.transmitToPeer(recipientDeviceId, packet)
        if (!sent) {
          offlineQueue.enqueue(packet)
          tx.broadcastToAllConnectedPeers(packet)
        }
      } else {
        // Enqueue offline and try multi-hop broadcast
        offlineQueue.enqueue(packet)
        tx.broadcastToAllConnectedPeers(packet)
      }
    } else {
      offlineQueue.enqueue(packet)
    }

    return packet
  }

  fun handleIncomingPacket(packet: MeshPacket, fromPeerId: String) {
    // 1. Deduplication check
    if (!seenPacketIds.add(packet.packetId)) {
      return // Duplicate packet already handled
    }

    registerPeer(fromPeerId)

    // 2. Direct message for local node
    if (packet.recipientDeviceId == localDeviceId) {
      if (packet.payloadType == MeshPayloadType.DELIVERY_ACK) {
        val ackedPacketId = String(packet.payload, Charsets.UTF_8)
        offlineQueue.acknowledgeDelivery(ackedPacketId)
        if (!_deliveryAcks.tryEmit(ackedPacketId)) {
          scope.launch(Dispatchers.Unconfined) { _deliveryAcks.emit(ackedPacketId) }
        }
      } else {
        if (!_incomingPackets.tryEmit(packet)) {
          scope.launch(Dispatchers.Unconfined) { _incomingPackets.emit(packet) }
        }
        // Automatically send delivery ACK back
        sendAck(packet.senderDeviceId, packet.packetId)
      }
      return
    }

    // 3. Channel or network broadcast
    if (packet.isChannel() || packet.isBroadcast()) {
      if (packet.isChannel()) {
        if (!_channelMessages.tryEmit(Pair(packet.recipientDeviceId, packet))) {
          scope.launch(Dispatchers.Unconfined) { _channelMessages.emit(Pair(packet.recipientDeviceId, packet)) }
        }
      } else {
        if (!_incomingPackets.tryEmit(packet)) {
          scope.launch(Dispatchers.Unconfined) { _incomingPackets.emit(packet) }
        }
      }

      // Forward to other peers if TTL > 1
      relayPacket(packet, fromPeerId)
      return
    }

    // 4. Multi-hop relay for another destination
    relayPacket(packet, fromPeerId)
  }

  private fun relayPacket(packet: MeshPacket, incomingPeerId: String) {
    val relayed = packet.decrementTtl() ?: return // Drop expired packet
    val tx = transmitter ?: return

    _meshPeers.value.forEach { peerId ->
      if (peerId != incomingPeerId && peerId != packet.senderDeviceId) {
        tx.transmitToPeer(peerId, relayed)
      }
    }
  }

  private fun sendAck(toDeviceId: String, acknowledgedPacketId: String) {
    val ackPacket = MeshPacket(
      packetId = UUID.randomUUID().toString(),
      senderDeviceId = localDeviceId,
      recipientDeviceId = toDeviceId,
      ttl = MeshPacket.DEFAULT_TTL,
      hopCount = 0,
      payloadType = MeshPayloadType.DELIVERY_ACK,
      payload = acknowledgedPacketId.toByteArray(Charsets.UTF_8),
    )
    seenPacketIds.add(ackPacket.packetId)
    val tx = transmitter
    if (tx != null) {
      if (isPeerConnected(toDeviceId)) {
        tx.transmitToPeer(toDeviceId, ackPacket)
      } else {
        tx.broadcastToAllConnectedPeers(ackPacket)
      }
    }
  }

  private fun drainOfflineQueueForPeer(peerId: String) {
    val queued = offlineQueue.drainForPeer(peerId)
    val tx = transmitter ?: return
    queued.forEach { packet ->
      tx.transmitToPeer(peerId, packet)
    }
  }

  fun hasSeenPacket(packetId: String): Boolean = seenPacketIds.contains(packetId)
}

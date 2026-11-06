package com.peykr.app.domain.mesh

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BleMeshEngineTest {

  @Test
  fun testDeduplicationCachePreventsLoop() = runTest {
    val engine = BleMeshEngine(localDeviceId = "node-A")
    val transmittedPackets = mutableListOf<MeshPacket>()

    engine.transmitter = object : MeshPacketTransmitter {
      override fun transmitToPeer(peerId: String, packet: MeshPacket): Boolean {
        transmittedPackets.add(packet)
        return true
      }
      override fun broadcastToAllConnectedPeers(packet: MeshPacket): Int {
        transmittedPackets.add(packet)
        return 1
      }
    }

    engine.registerPeer("node-B")
    engine.registerPeer("node-C")

    val packet = MeshPacket(
      packetId = "dup-123",
      senderDeviceId = "node-origin",
      recipientDeviceId = "node-target",
      ttl = 5,
      hopCount = 1,
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = "Loop test".toByteArray(StandardCharsets.UTF_8),
    )

    // First arrival: should be relayed
    engine.handleIncomingPacket(packet, fromPeerId = "node-B")
    assertTrue(engine.hasSeenPacket("dup-123"))
    val firstRelayCount = transmittedPackets.size
    assertTrue("Packet should be relayed on first arrival", firstRelayCount > 0)

    // Second arrival of same packetId (loop back from C): must be discarded!
    engine.handleIncomingPacket(packet, fromPeerId = "node-C")
    assertEquals("Duplicate packet must be discarded without re-transmitting", firstRelayCount, transmittedPackets.size)
  }

  @Test
  fun testDirectMessageArrivalEmitsAndSendsAck() = runTest {
    val engine = BleMeshEngine(localDeviceId = "node-B")
    val transmittedPackets = mutableListOf<MeshPacket>()

    engine.transmitter = object : MeshPacketTransmitter {
      override fun transmitToPeer(peerId: String, packet: MeshPacket): Boolean {
        transmittedPackets.add(packet)
        return true
      }
      override fun broadcastToAllConnectedPeers(packet: MeshPacket): Int {
        transmittedPackets.add(packet)
        return 1
      }
    }

    engine.registerPeer("node-A")

    val messagePacket = MeshPacket(
      packetId = "msg-direct-1",
      senderDeviceId = "node-A",
      recipientDeviceId = "node-B", // dest is node-B
      ttl = 4,
      hopCount = 0,
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = "Hello Node B".toByteArray(StandardCharsets.UTF_8),
    )

    engine.handleIncomingPacket(messagePacket, fromPeerId = "node-A")

    // Node B must have automatically sent back a DELIVERY_ACK
    val ackPacket = transmittedPackets.firstOrNull { it.payloadType == MeshPayloadType.DELIVERY_ACK }
    assertTrue("Delivery ACK must be transmitted back to sender", ackPacket != null)
    assertEquals("node-A", ackPacket!!.recipientDeviceId)
    assertEquals("msg-direct-1", String(ackPacket.payload, StandardCharsets.UTF_8))
  }
}

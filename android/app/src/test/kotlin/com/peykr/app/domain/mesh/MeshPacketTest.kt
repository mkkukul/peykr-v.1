package com.peykr.app.domain.mesh

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class MeshPacketTest {

  @Test
  fun testPacketSerializationAndDeserialization() {
    val payload = "Selam Peykr Mesh!".toByteArray(StandardCharsets.UTF_8)
    val original = MeshPacket(
      packetId = "test-packet-123",
      senderDeviceId = "device-sender-A",
      recipientDeviceId = "device-recipient-B",
      ttl = 5,
      hopCount = 2,
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = payload,
      timestamp = 1700000000000L,
      signature = "sig-abc-xyz",
    )

    val bytes = original.toByteArray()
    assertTrue(bytes.size >= 25)

    val restored = MeshPacket.fromByteArray(bytes).getOrThrow()
    assertEquals(original.packetId, restored.packetId)
    assertEquals(original.senderDeviceId, restored.senderDeviceId)
    assertEquals(original.recipientDeviceId, restored.recipientDeviceId)
    assertEquals(original.ttl, restored.ttl)
    assertEquals(original.hopCount, restored.hopCount)
    assertEquals(original.payloadType, restored.payloadType)
    assertEquals(original.timestamp, restored.timestamp)
    assertEquals(original.signature, restored.signature)
    assertArrayEquals(original.payload, restored.payload)
  }

  @Test
  fun testTtlDecrement() {
    val packet = MeshPacket(
      senderDeviceId = "A",
      recipientDeviceId = "B",
      ttl = 3,
      hopCount = 0,
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = ByteArray(0),
    )

    val hop1 = packet.decrementTtl()
    assertNotNull(hop1)
    assertEquals(2, hop1!!.ttl)
    assertEquals(1, hop1.hopCount)

    val hop2 = hop1.decrementTtl()
    assertNotNull(hop2)
    assertEquals(1, hop2!!.ttl)
    assertEquals(2, hop2.hopCount)

    // When TTL reaches 1, further decrement should drop packet (return null)
    val hop3 = hop2.decrementTtl()
    assertNull(hop3)
  }

  @Test
  fun testChannelAndBroadcastDetection() {
    val direct = MeshPacket(
      senderDeviceId = "A",
      recipientDeviceId = "dev-123",
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = ByteArray(0),
    )
    val channel = MeshPacket(
      senderDeviceId = "A",
      recipientDeviceId = "#genel",
      payloadType = MeshPayloadType.CHANNEL_BROADCAST,
      payload = ByteArray(0),
    )
    val broadcast = MeshPacket(
      senderDeviceId = "A",
      recipientDeviceId = "*",
      payloadType = MeshPayloadType.CHANNEL_BROADCAST,
      payload = ByteArray(0),
    )

    assertTrue(!direct.isBroadcast())
    assertTrue(!direct.isChannel())
    assertTrue(channel.isChannel())
    assertTrue(channel.isBroadcast())
    assertTrue(broadcast.isBroadcast())
  }
}

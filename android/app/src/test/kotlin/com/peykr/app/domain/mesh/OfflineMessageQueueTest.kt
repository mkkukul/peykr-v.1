package com.peykr.app.domain.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class OfflineMessageQueueTest {

  @Test
  fun testEnqueueAndDrainForPeer() {
    val queue = OfflineMessageQueue(maxQueueSize = 10)

    val packet1 = MeshPacket(
      packetId = "p1",
      senderDeviceId = "me",
      recipientDeviceId = "peer-alpha",
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = "Hi Alpha".toByteArray(StandardCharsets.UTF_8),
    )
    val packet2 = MeshPacket(
      packetId = "p2",
      senderDeviceId = "me",
      recipientDeviceId = "peer-beta",
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = "Hi Beta".toByteArray(StandardCharsets.UTF_8),
    )

    queue.enqueue(packet1)
    queue.enqueue(packet2)
    assertEquals(2, queue.getPendingCount())

    // Draining for peer-alpha only returns packet1
    val drainedAlpha = queue.drainForPeer("peer-alpha")
    assertEquals(1, drainedAlpha.size)
    assertEquals("p1", drainedAlpha[0].packetId)

    // Draining for peer-beta returns packet2
    val drainedBeta = queue.drainForPeer("peer-beta")
    assertEquals(1, drainedBeta.size)
    assertEquals("p2", drainedBeta[0].packetId)
  }

  @Test
  fun testAcknowledgmentRemovesPacket() {
    val queue = OfflineMessageQueue()

    val packet = MeshPacket(
      packetId = "p-ack-test",
      senderDeviceId = "me",
      recipientDeviceId = "peer-charlie",
      payloadType = MeshPayloadType.TEXT_MESSAGE,
      payload = "Test ACK".toByteArray(StandardCharsets.UTF_8),
    )

    queue.enqueue(packet)
    assertEquals(1, queue.getPendingCount())

    val acknowledged = queue.acknowledgeDelivery("p-ack-test")
    assertTrue(acknowledged)
    assertEquals(0, queue.getPendingCount())

    val ackNonExistent = queue.acknowledgeDelivery("non-existent")
    assertFalse(ackNonExistent)
  }
}

package com.peykr.app.domain.mesh

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

data class QueuedMessageItem(
  val packet: MeshPacket,
  val queuedEpochMillis: Long = System.currentTimeMillis(),
  val maxTries: Int = 10,
  var tryCount: Int = 0,
)

class OfflineMessageQueue(
  private val maxQueueSize: Int = 500,
  private val maxTtlMillis: Long = 7 * 24 * 60 * 60 * 1000L, // 7 days
) {
  // Keyed by recipientDeviceId -> queue of packets
  private val queueByRecipient = ConcurrentHashMap<String, ConcurrentLinkedQueue<QueuedMessageItem>>()

  fun enqueue(packet: MeshPacket): Boolean {
    cleanupExpired()
    val totalCount = queueByRecipient.values.sumOf { it.size }
    if (totalCount >= maxQueueSize) {
      return false
    }

    val recipientQueue = queueByRecipient.computeIfAbsent(packet.recipientDeviceId) {
      ConcurrentLinkedQueue()
    }
    // Prevent duplicate enqueues
    if (recipientQueue.any { it.packet.packetId == packet.packetId }) {
      return true
    }

    recipientQueue.add(QueuedMessageItem(packet))
    return true
  }

  fun drainForPeer(peerDeviceId: String): List<MeshPacket> {
    cleanupExpired()
    val list = mutableListOf<MeshPacket>()

    // Direct packets for this peer
    queueByRecipient[peerDeviceId]?.let { q ->
      val iterator = q.iterator()
      while (iterator.hasNext()) {
        val item = iterator.next()
        item.tryCount++
        list.add(item.packet)
        if (item.tryCount >= item.maxTries) {
          iterator.remove()
        }
      }
    }

    // Broadcast or channel packets
    queueByRecipient.keys.filter { it.startsWith("#") || it == "*" }.forEach { key ->
      queueByRecipient[key]?.let { q ->
        q.forEach { item ->
          list.add(item.packet)
        }
      }
    }

    return list
  }

  fun acknowledgeDelivery(packetId: String): Boolean {
    var removed = false
    queueByRecipient.values.forEach { q ->
      val it = q.iterator()
      while (it.hasNext()) {
        if (it.next().packet.packetId == packetId) {
          it.remove()
          removed = true
        }
      }
    }
    return removed
  }

  fun getPendingCount(): Int {
    return queueByRecipient.values.sumOf { it.size }
  }

  fun getPendingPacketsForRecipient(recipientId: String): List<MeshPacket> {
    return queueByRecipient[recipientId]?.map { it.packet } ?: emptyList()
  }

  fun clear() {
    queueByRecipient.clear()
  }

  private fun cleanupExpired() {
    val now = System.currentTimeMillis()
    queueByRecipient.values.forEach { q ->
      q.removeIf { now - it.queuedEpochMillis > maxTtlMillis }
    }
    queueByRecipient.entries.removeIf { it.value.isEmpty() }
  }
}

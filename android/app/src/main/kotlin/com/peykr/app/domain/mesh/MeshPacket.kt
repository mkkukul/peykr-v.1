package com.peykr.app.domain.mesh

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class MeshPayloadType(val code: Byte) {
  TEXT_MESSAGE(1),
  DELIVERY_ACK(2),
  PEER_ANNOUNCEMENT(3),
  CHANNEL_BROADCAST(4),
  FILE_PROPOSAL(5),
  ROUTING_PROBE(6);

  companion object {
    fun fromCode(code: Byte): MeshPayloadType =
      entries.firstOrNull { it.code == code } ?: TEXT_MESSAGE
  }
}

data class MeshPacket(
  val packetId: String = UUID.randomUUID().toString(),
  val senderDeviceId: String,
  val recipientDeviceId: String, // Device ID, "*" for broadcast, or "#channel"
  val ttl: Int = DEFAULT_TTL,
  val hopCount: Int = 0,
  val payloadType: MeshPayloadType,
  val payload: ByteArray,
  val timestamp: Long = System.currentTimeMillis(),
  val signature: String = "",
) {
  fun isChannel(): Boolean = recipientDeviceId.startsWith("#")
  fun isBroadcast(): Boolean = recipientDeviceId == "*" || isChannel()

  fun decrementTtl(): MeshPacket? {
    if (ttl <= 1) return null
    return copy(ttl = ttl - 1, hopCount = hopCount + 1)
  }

  fun toByteArray(): ByteArray {
    val packetIdBytes = packetId.toByteArray(StandardCharsets.UTF_8)
    val senderBytes = senderDeviceId.toByteArray(StandardCharsets.UTF_8)
    val recipientBytes = recipientDeviceId.toByteArray(StandardCharsets.UTF_8)
    val sigBytes = signature.toByteArray(StandardCharsets.UTF_8)

    val buffer = ByteBuffer.allocate(
      4 + // magic 0x5045594B (PEYK)
      1 + // version 1
      1 + // payload type code
      1 + // ttl
      1 + // hop count
      8 + // timestamp
      2 + packetIdBytes.size +
      2 + senderBytes.size +
      2 + recipientBytes.size +
      2 + sigBytes.size +
      4 + payload.size
    )

    buffer.put(MAGIC)
    buffer.put(VERSION)
    buffer.put(payloadType.code)
    buffer.put(ttl.toByte())
    buffer.put(hopCount.toByte())
    buffer.putLong(timestamp)

    buffer.putShort(packetIdBytes.size.toShort())
    buffer.put(packetIdBytes)

    buffer.putShort(senderBytes.size.toShort())
    buffer.put(senderBytes)

    buffer.putShort(recipientBytes.size.toShort())
    buffer.put(recipientBytes)

    buffer.putShort(sigBytes.size.toShort())
    buffer.put(sigBytes)

    buffer.putInt(payload.size)
    buffer.put(payload)

    return buffer.array()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MeshPacket

    if (packetId != other.packetId) return false
    if (senderDeviceId != other.senderDeviceId) return false
    if (recipientDeviceId != other.recipientDeviceId) return false
    if (ttl != other.ttl) return false
    if (hopCount != other.hopCount) return false
    if (payloadType != other.payloadType) return false
    if (!payload.contentEquals(other.payload)) return false
    if (timestamp != other.timestamp) return false
    if (signature != other.signature) return false

    return true
  }

  override fun hashCode(): Int {
    var result = packetId.hashCode()
    result = 31 * result + senderDeviceId.hashCode()
    result = 31 * result + recipientDeviceId.hashCode()
    result = 31 * result + ttl
    result = 31 * result + hopCount
    result = 31 * result + payloadType.hashCode()
    result = 31 * result + payload.contentHashCode()
    result = 31 * result + timestamp.hashCode()
    result = 31 * result + signature.hashCode()
    return result
  }

  companion object {
    val MAGIC = byteArrayOf(0x50, 0x45, 0x59, 0x4B) // "PEYK"
    const val VERSION: Byte = 1
    const val DEFAULT_TTL = 7
    const val MAX_PAYLOAD_SIZE = 64 * 1024 // 64 KB

    fun fromByteArray(bytes: ByteArray): Result<MeshPacket> = runCatching {
      require(bytes.size >= 25) { "Packet byte array too short: ${bytes.size}" }
      val buffer = ByteBuffer.wrap(bytes)

      val magic = ByteArray(4)
      buffer.get(magic)
      require(magic.contentEquals(MAGIC)) { "Invalid magic header for Peykr Mesh Packet" }

      val version = buffer.get()
      require(version == VERSION) { "Unsupported packet version: $version" }

      val payloadTypeCode = buffer.get()
      val payloadType = MeshPayloadType.fromCode(payloadTypeCode)
      val ttl = buffer.get().toInt()
      val hopCount = buffer.get().toInt()
      val timestamp = buffer.getLong()

      val packetIdLen = buffer.getShort().toInt()
      val packetIdBytes = ByteArray(packetIdLen)
      buffer.get(packetIdBytes)
      val packetId = String(packetIdBytes, StandardCharsets.UTF_8)

      val senderLen = buffer.getShort().toInt()
      val senderBytes = ByteArray(senderLen)
      buffer.get(senderBytes)
      val senderDeviceId = String(senderBytes, StandardCharsets.UTF_8)

      val recipientLen = buffer.getShort().toInt()
      val recipientBytes = ByteArray(recipientLen)
      buffer.get(recipientBytes)
      val recipientDeviceId = String(recipientBytes, StandardCharsets.UTF_8)

      val sigLen = buffer.getShort().toInt()
      val sigBytes = ByteArray(sigLen)
      buffer.get(sigBytes)
      val signature = String(sigBytes, StandardCharsets.UTF_8)

      val payloadLen = buffer.getInt()
      require(payloadLen in 0..MAX_PAYLOAD_SIZE) { "Payload size out of bounds: $payloadLen" }
      val payload = ByteArray(payloadLen)
      buffer.get(payload)

      MeshPacket(
        packetId = packetId,
        senderDeviceId = senderDeviceId,
        recipientDeviceId = recipientDeviceId,
        ttl = ttl,
        hopCount = hopCount,
        payloadType = payloadType,
        payload = payload,
        timestamp = timestamp,
        signature = signature,
      )
    }
  }
}

package com.peykr.app.domain.pairing

import com.peykr.app.domain.contacts.PairingStatus
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.text.Normalizer
import java.util.Locale

enum class PairingRole {
  INITIATOR,
  RESPONDER,
}

enum class PairingSessionState {
  CREATED,
  QR_READY,
  REQUEST_RECEIVED,
  VERIFIED,
  WAITING_FOR_LOCAL_APPROVAL,
  LOCAL_APPROVED,
  WAITING_FOR_REMOTE_RESPONSE,
  COMPLETION_SENT,
  COMPLETION_RECEIVED,
  PAIRED,
  INDETERMINATE,
  EXPIRED,
  REJECTED,
  FAILED,
}

object StrictBase64 {
  private val BASE64_REGEX = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$".toRegex()

  fun decode(input: String): ByteArray {
    val clean = input.trim()
    if (clean.isEmpty() || !clean.matches(BASE64_REGEX)) {
      throw PairingException.StrictDecodingFailure("Input is not valid RFC 4648 standard Base64: $clean")
    }
    return try {
      java.util.Base64.getDecoder().decode(clean)
    } catch (e: Throwable) {
      throw PairingException.StrictDecodingFailure("Base64 decoding failed: ${e.message}", e)
    }
  }

  fun encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
}

data class RemoteDeviceIdentity(
  val deviceId: String,
  val publicKeyBase64: String,
  val displayName: String,
) {
  fun verifyDeviceId(): Boolean {
    return try {
      val decoded = StrictBase64.decode(publicKeyBase64)
      val keySpec = X509EncodedKeySpec(decoded)
      val keyFactory = KeyFactory.getInstance("EC")
      val key = keyFactory.generatePublic(keySpec)
      if (key.algorithm != "EC") return false

      val digest = MessageDigest.getInstance("SHA-256").digest(decoded)
      val computedId = digest.joinToString("") { "%02x".format(Locale.US, it) }
      computedId.equals(deviceId, ignoreCase = false)
    } catch (_: Throwable) {
      false
    }
  }

  companion object {
    const val MAX_DISPLAY_NAME_LENGTH = 64

    fun normalizeDisplayName(raw: String): String {
      val nfc = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
      val sanitized = nfc.filter { ch ->
        !ch.isISOControl() &&
          ch !in '\u200B'..'\u200F' &&
          ch !in '\u202A'..'\u202E' &&
          ch !in '\u2066'..'\u2069'
      }
      return sanitized.take(MAX_DISPLAY_NAME_LENGTH).trim()
    }
  }
}

data class PairingTranscript(
  val protocolVersion: Int,
  val sessionId: String,
  val initiatorDeviceId: String,
  val initiatorPublicKeyBase64: String,
  val initiatorNonceBase64: String,
  val responderDeviceId: String,
  val responderPublicKeyBase64: String,
  val responderNonceBase64: String,
  val expiresAtEpochMillis: Long,
) {
  fun toCanonicalBytes(): ByteArray {
    val baos = ByteArrayOutputStream()
    DataOutputStream(baos).use { dos ->
      dos.write("PEYKR_PAIR_TRANSCRIPT_V1\n".toByteArray(Charsets.UTF_8))
      dos.writeInt(protocolVersion)
      writeField(dos, sessionId)
      writeField(dos, initiatorDeviceId)
      writeField(dos, initiatorPublicKeyBase64)
      writeField(dos, initiatorNonceBase64)
      writeField(dos, responderDeviceId)
      writeField(dos, responderPublicKeyBase64)
      writeField(dos, responderNonceBase64)
      dos.writeLong(expiresAtEpochMillis)
      dos.flush()
    }
    return baos.toByteArray()
  }

  companion object {
    internal fun writeField(dos: DataOutputStream, value: String) {
      val bytes = value.toByteArray(Charsets.UTF_8)
      dos.writeInt(bytes.size)
      dos.write(bytes)
    }
  }
}

data class PairingConfirmationTranscript(
  val protocolVersion: Int,
  val sessionId: String,
  val initiatorSignatureBase64: String,
  val responderSignatureBase64: String,
) {
  fun toCanonicalBytes(): ByteArray {
    val baos = ByteArrayOutputStream()
    DataOutputStream(baos).use { dos ->
      dos.write("PEYKR_PAIR_CONFIRM_V1\n".toByteArray(Charsets.UTF_8))
      dos.writeInt(protocolVersion)
      PairingTranscript.writeField(dos, sessionId)
      PairingTranscript.writeField(dos, initiatorSignatureBase64)
      PairingTranscript.writeField(dos, responderSignatureBase64)
      dos.flush()
    }
    return baos.toByteArray()
  }
}

internal object JsonUtils {
  fun escape(s: String): String = buildString {
    for (ch in s) {
      when (ch) {
        '\\' -> append("\\\\")
        '\"' -> append("\\\"")
        '\b' -> append("\\b")
        '\u000c' -> append("\\f")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> {
          if (ch < ' ') {
            append(String.format("\\u%04x", ch.code))
          } else {
            append(ch)
          }
        }
      }
    }
  }

  fun extractString(json: String, key: String): String? {
    val pattern = "\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"".toRegex()
    val match = pattern.find(json) ?: return null
    return match.groupValues[1]
      .replace("\\\"", "\"")
      .replace("\\\\", "\\")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\b", "\b")
      .replace("\\f", "\u000c")
  }

  fun extractInt(json: String, key: String): Int? {
    val pattern = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
  }

  fun extractLong(json: String, key: String): Long? {
    val pattern = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
    return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
  }
}

enum class QrMessageType {
  INITIAL_PAYLOAD,
  PAIRING_REQUEST,
  PAIRING_RESPONSE,
  PAIRING_CONFIRMATION,
  UNKNOWN,
}

object QrMessageClassifier {
  fun classify(payload: String): QrMessageType {
    val trimmed = payload.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return QrMessageType.UNKNOWN

    val type = JsonUtils.extractString(trimmed, "t")
    if (type != null) {
      return when (type) {
        "init" -> QrMessageType.INITIAL_PAYLOAD
        "req" -> QrMessageType.PAIRING_REQUEST
        "resp" -> QrMessageType.PAIRING_RESPONSE
        "conf" -> QrMessageType.PAIRING_CONFIRMATION
        else -> QrMessageType.UNKNOWN
      }
    }

    if (trimmed.contains("\"exp\"") && trimmed.contains("\"pk\"") && !trimmed.contains("\"sig\"")) {
      return QrMessageType.INITIAL_PAYLOAD
    }

    return QrMessageType.UNKNOWN
  }
}

data class PairingQrPayload(
  val version: Int,
  val sessionId: String,
  val initiatorDeviceId: String,
  val initiatorPublicKeyBase64: String,
  val initiatorDisplayName: String,
  val initiatorNonceBase64: String,
  val expiresAtEpochMillis: Long,
) {
  fun serialize(): String {
    return buildString {
      append("{\"t\":\"init\",\"v\":")
      append(version)
      append(",\"sid\":\"")
      append(JsonUtils.escape(sessionId))
      append("\",\"id\":\"")
      append(JsonUtils.escape(initiatorDeviceId))
      append("\",\"pk\":\"")
      append(JsonUtils.escape(initiatorPublicKeyBase64))
      append("\",\"name\":\"")
      append(JsonUtils.escape(RemoteDeviceIdentity.normalizeDisplayName(initiatorDisplayName)))
      append("\",\"nonce\":\"")
      append(JsonUtils.escape(initiatorNonceBase64))
      append("\",\"exp\":")
      append(expiresAtEpochMillis)
      append("}")
    }
  }

  companion object {
    const val CURRENT_VERSION = 1
    const val MAX_QR_PAYLOAD_LENGTH = 2048
    internal val UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".toRegex()
    internal val DEVICE_ID_REGEX = "^[0-9a-f]{64}$".toRegex()

    fun parse(payload: String): Result<PairingQrPayload> {
      if (payload.length > MAX_QR_PAYLOAD_LENGTH) {
        return Result.failure(PairingException.OversizedPayload("Payload exceeds $MAX_QR_PAYLOAD_LENGTH characters"))
      }
      val trimmed = payload.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return Result.failure(PairingException.MalformedPayload("QR payload must be a JSON object"))
      }

      return runCatching {
        val version = JsonUtils.extractInt(trimmed, "v") ?: throw PairingException.MalformedPayload("Missing or invalid 'v'")
        val sessionId = JsonUtils.extractString(trimmed, "sid") ?: throw PairingException.MalformedPayload("Missing 'sid'")
        val initiatorDeviceId = JsonUtils.extractString(trimmed, "id") ?: throw PairingException.MalformedPayload("Missing 'id'")
        val initiatorPublicKeyBase64 = JsonUtils.extractString(trimmed, "pk") ?: throw PairingException.MalformedPayload("Missing 'pk'")
        val initiatorDisplayName = JsonUtils.extractString(trimmed, "name") ?: throw PairingException.MalformedPayload("Missing 'name'")
        val initiatorNonceBase64 = JsonUtils.extractString(trimmed, "nonce") ?: throw PairingException.MalformedPayload("Missing 'nonce'")
        val expiresAtEpochMillis = JsonUtils.extractLong(trimmed, "exp") ?: throw PairingException.MalformedPayload("Missing or invalid 'exp'")

        if (version != CURRENT_VERSION) {
          throw PairingException.ProtocolVersionMismatch("Expected protocol version $CURRENT_VERSION but got $version")
        }

        if (!sessionId.matches(UUID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid session ID UUID format: $sessionId")
        }

        if (!initiatorDeviceId.matches(DEVICE_ID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid device ID format (must be 64-char hex): $initiatorDeviceId")
        }

        val nonceBytes = StrictBase64.decode(initiatorNonceBase64)
        if (nonceBytes.size != 16) {
          throw PairingException.MalformedPayload("Initiator nonce must be exactly 16 bytes (got ${nonceBytes.size})")
        }

        PairingQrPayload(
          version = version,
          sessionId = sessionId,
          initiatorDeviceId = initiatorDeviceId,
          initiatorPublicKeyBase64 = initiatorPublicKeyBase64,
          initiatorDisplayName = RemoteDeviceIdentity.normalizeDisplayName(initiatorDisplayName),
          initiatorNonceBase64 = initiatorNonceBase64,
          expiresAtEpochMillis = expiresAtEpochMillis,
        )
      }
    }
  }
}

data class PairingRequest(
  val protocolVersion: Int,
  val sessionId: String,
  val responderDeviceId: String,
  val responderPublicKeyBase64: String,
  val responderDisplayName: String,
  val responderNonceBase64: String,
  val responderSignatureBase64: String,
) {
  fun serialize(): String {
    return buildString {
      append("{\"t\":\"req\",\"v\":")
      append(protocolVersion)
      append(",\"sid\":\"")
      append(JsonUtils.escape(sessionId))
      append("\",\"id\":\"")
      append(JsonUtils.escape(responderDeviceId))
      append("\",\"pk\":\"")
      append(JsonUtils.escape(responderPublicKeyBase64))
      append("\",\"name\":\"")
      append(JsonUtils.escape(RemoteDeviceIdentity.normalizeDisplayName(responderDisplayName)))
      append("\",\"nonce\":\"")
      append(JsonUtils.escape(responderNonceBase64))
      append("\",\"sig\":\"")
      append(JsonUtils.escape(responderSignatureBase64))
      append("\"}")
    }
  }

  companion object {
    fun parse(payload: String): Result<PairingRequest> {
      if (payload.length > PairingQrPayload.MAX_QR_PAYLOAD_LENGTH) {
        return Result.failure(PairingException.OversizedPayload("Payload exceeds ${PairingQrPayload.MAX_QR_PAYLOAD_LENGTH} characters"))
      }
      val trimmed = payload.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return Result.failure(PairingException.MalformedPayload("QR payload must be a JSON object"))
      }

      return runCatching {
        val version = JsonUtils.extractInt(trimmed, "v") ?: throw PairingException.MalformedPayload("Missing or invalid 'v'")
        val sessionId = JsonUtils.extractString(trimmed, "sid") ?: throw PairingException.MalformedPayload("Missing 'sid'")
        val responderDeviceId = JsonUtils.extractString(trimmed, "id") ?: throw PairingException.MalformedPayload("Missing 'id'")
        val responderPublicKeyBase64 = JsonUtils.extractString(trimmed, "pk") ?: throw PairingException.MalformedPayload("Missing 'pk'")
        val responderDisplayName = JsonUtils.extractString(trimmed, "name") ?: throw PairingException.MalformedPayload("Missing 'name'")
        val responderNonceBase64 = JsonUtils.extractString(trimmed, "nonce") ?: throw PairingException.MalformedPayload("Missing 'nonce'")
        val responderSignatureBase64 = JsonUtils.extractString(trimmed, "sig") ?: throw PairingException.MalformedPayload("Missing 'sig'")

        if (version != PairingQrPayload.CURRENT_VERSION) {
          throw PairingException.ProtocolVersionMismatch("Expected protocol version ${PairingQrPayload.CURRENT_VERSION} but got $version")
        }

        if (!sessionId.matches(PairingQrPayload.UUID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid session ID UUID format: $sessionId")
        }

        if (!responderDeviceId.matches(PairingQrPayload.DEVICE_ID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid device ID format (must be 64-char hex): $responderDeviceId")
        }

        val nonceBytes = StrictBase64.decode(responderNonceBase64)
        if (nonceBytes.size != 16) {
          throw PairingException.MalformedPayload("Responder nonce must be exactly 16 bytes (got ${nonceBytes.size})")
        }

        StrictBase64.decode(responderPublicKeyBase64)
        StrictBase64.decode(responderSignatureBase64)

        PairingRequest(
          protocolVersion = version,
          sessionId = sessionId,
          responderDeviceId = responderDeviceId,
          responderPublicKeyBase64 = responderPublicKeyBase64,
          responderDisplayName = RemoteDeviceIdentity.normalizeDisplayName(responderDisplayName),
          responderNonceBase64 = responderNonceBase64,
          responderSignatureBase64 = responderSignatureBase64,
        )
      }
    }
  }
}

data class PairingResponse(
  val protocolVersion: Int,
  val sessionId: String,
  val initiatorDeviceId: String,
  val initiatorSignatureBase64: String,
) {
  fun serialize(): String {
    return buildString {
      append("{\"t\":\"resp\",\"v\":")
      append(protocolVersion)
      append(",\"sid\":\"")
      append(JsonUtils.escape(sessionId))
      append("\",\"id\":\"")
      append(JsonUtils.escape(initiatorDeviceId))
      append("\",\"sig\":\"")
      append(JsonUtils.escape(initiatorSignatureBase64))
      append("\"}")
    }
  }

  companion object {
    fun parse(payload: String): Result<PairingResponse> {
      if (payload.length > PairingQrPayload.MAX_QR_PAYLOAD_LENGTH) {
        return Result.failure(PairingException.OversizedPayload("Payload exceeds ${PairingQrPayload.MAX_QR_PAYLOAD_LENGTH} characters"))
      }
      val trimmed = payload.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return Result.failure(PairingException.MalformedPayload("QR payload must be a JSON object"))
      }

      return runCatching {
        val version = JsonUtils.extractInt(trimmed, "v") ?: throw PairingException.MalformedPayload("Missing or invalid 'v'")
        val sessionId = JsonUtils.extractString(trimmed, "sid") ?: throw PairingException.MalformedPayload("Missing 'sid'")
        val initiatorDeviceId = JsonUtils.extractString(trimmed, "id") ?: throw PairingException.MalformedPayload("Missing 'id'")
        val initiatorSignatureBase64 = JsonUtils.extractString(trimmed, "sig") ?: throw PairingException.MalformedPayload("Missing 'sig'")

        if (version != PairingQrPayload.CURRENT_VERSION) {
          throw PairingException.ProtocolVersionMismatch("Expected protocol version ${PairingQrPayload.CURRENT_VERSION} but got $version")
        }

        if (!sessionId.matches(PairingQrPayload.UUID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid session ID UUID format: $sessionId")
        }

        if (!initiatorDeviceId.matches(PairingQrPayload.DEVICE_ID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid device ID format (must be 64-char hex): $initiatorDeviceId")
        }

        StrictBase64.decode(initiatorSignatureBase64)

        PairingResponse(
          protocolVersion = version,
          sessionId = sessionId,
          initiatorDeviceId = initiatorDeviceId,
          initiatorSignatureBase64 = initiatorSignatureBase64,
        )
      }
    }
  }
}

data class PairingConfirmation(
  val protocolVersion: Int,
  val sessionId: String,
  val responderDeviceId: String,
  val confirmationSignatureBase64: String,
) {
  fun serialize(): String {
    return buildString {
      append("{\"t\":\"conf\",\"v\":")
      append(protocolVersion)
      append(",\"sid\":\"")
      append(JsonUtils.escape(sessionId))
      append("\",\"id\":\"")
      append(JsonUtils.escape(responderDeviceId))
      append("\",\"sig\":\"")
      append(JsonUtils.escape(confirmationSignatureBase64))
      append("\"}")
    }
  }

  companion object {
    fun parse(payload: String): Result<PairingConfirmation> {
      if (payload.length > PairingQrPayload.MAX_QR_PAYLOAD_LENGTH) {
        return Result.failure(PairingException.OversizedPayload("Payload exceeds ${PairingQrPayload.MAX_QR_PAYLOAD_LENGTH} characters"))
      }
      val trimmed = payload.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return Result.failure(PairingException.MalformedPayload("QR payload must be a JSON object"))
      }

      return runCatching {
        val version = JsonUtils.extractInt(trimmed, "v") ?: throw PairingException.MalformedPayload("Missing or invalid 'v'")
        val sessionId = JsonUtils.extractString(trimmed, "sid") ?: throw PairingException.MalformedPayload("Missing 'sid'")
        val responderDeviceId = JsonUtils.extractString(trimmed, "id") ?: throw PairingException.MalformedPayload("Missing 'id'")
        val confirmationSignatureBase64 = JsonUtils.extractString(trimmed, "sig") ?: throw PairingException.MalformedPayload("Missing 'sig'")

        if (version != PairingQrPayload.CURRENT_VERSION) {
          throw PairingException.ProtocolVersionMismatch("Expected protocol version ${PairingQrPayload.CURRENT_VERSION} but got $version")
        }

        if (!sessionId.matches(PairingQrPayload.UUID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid session ID UUID format: $sessionId")
        }

        if (!responderDeviceId.matches(PairingQrPayload.DEVICE_ID_REGEX)) {
          throw PairingException.MalformedPayload("Invalid device ID format (must be 64-char hex): $responderDeviceId")
        }

        StrictBase64.decode(confirmationSignatureBase64)

        PairingConfirmation(
          protocolVersion = version,
          sessionId = sessionId,
          responderDeviceId = responderDeviceId,
          confirmationSignatureBase64 = confirmationSignatureBase64,
        )
      }
    }
  }
}

data class PairingSession(
  val sessionId: String,
  val role: PairingRole,
  val state: PairingSessionState,
  val localDeviceId: String,
  val localPublicKeyBase64: String,
  val localDisplayName: String,
  val localNonceBase64: String,
  val remoteIdentity: RemoteDeviceIdentity? = null,
  val remoteNonceBase64: String? = null,
  val transcript: PairingTranscript? = null,
  val remoteSignatureBase64: String? = null,
  val localSignatureBase64: String? = null,
  val confirmationSignatureBase64: String? = null,
  val createdAtEpochMillis: Long,
  val expiresAtEpochMillis: Long,
) {
  fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean = nowMillis > expiresAtEpochMillis
}

sealed class PairingException(message: String, cause: Throwable? = null) : Exception(message, cause) {
  class ExpiredSession(message: String) : PairingException(message)
  class InvalidStateTransition(val current: PairingSessionState, val target: PairingSessionState) :
    PairingException("Invalid state transition from $current to $target")
  class DeviceIdMismatch(val claimedId: String, val computedId: String) :
    PairingException("Claimed device ID $claimedId does not match computed public key digest $computedId")
  class SelfPairingProhibited(message: String) : PairingException(message)
  class InvalidSignature(message: String) : PairingException(message)
  class Unauthorized(message: String) : PairingException(message)
  class MalformedPayload(message: String) : PairingException(message)
  class OversizedPayload(message: String) : PairingException(message)
  class ProtocolVersionMismatch(message: String) : PairingException(message)
  class ContactAlreadyPaired(val deviceId: String) :
    PairingException("Contact with device ID $deviceId is already trusted and paired")
  class StrictDecodingFailure(message: String, cause: Throwable? = null) : PairingException(message, cause)
  class PersistenceFailure(message: String, cause: Throwable? = null) : PairingException(message, cause)
  class SessionNotFound(val sessionId: String) : PairingException("Pairing session $sessionId was not found")
  class SessionIndeterminate(message: String) : PairingException(message)
}

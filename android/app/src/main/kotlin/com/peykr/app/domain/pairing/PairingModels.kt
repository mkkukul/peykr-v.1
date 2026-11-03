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
    fun normalizeDisplayName(raw: String): String =
      Normalizer.normalize(raw.trim(), Normalizer.Form.NFC)
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
      append("{\"v\":")
      append(version)
      append(",\"sid\":\"")
      append(escape(sessionId))
      append("\",\"id\":\"")
      append(escape(initiatorDeviceId))
      append("\",\"pk\":\"")
      append(escape(initiatorPublicKeyBase64))
      append("\",\"name\":\"")
      append(escape(RemoteDeviceIdentity.normalizeDisplayName(initiatorDisplayName)))
      append("\",\"nonce\":\"")
      append(escape(initiatorNonceBase64))
      append("\",\"exp\":")
      append(expiresAtEpochMillis)
      append("}")
    }
  }

  companion object {
    const val CURRENT_VERSION = 1
    const val MAX_QR_PAYLOAD_LENGTH = 2048
    private val UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$".toRegex()
    private val DEVICE_ID_REGEX = "^[0-9a-f]{64}$".toRegex()

    fun parse(payload: String): Result<PairingQrPayload> {
      if (payload.length > MAX_QR_PAYLOAD_LENGTH) {
        return Result.failure(PairingException.OversizedPayload("Payload exceeds $MAX_QR_PAYLOAD_LENGTH characters"))
      }
      val trimmed = payload.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return Result.failure(PairingException.MalformedPayload("QR payload must be a JSON object"))
      }

      return runCatching {
        val version = extractInt(trimmed, "v") ?: throw PairingException.MalformedPayload("Missing or invalid 'v'")
        val sessionId = extractString(trimmed, "sid") ?: throw PairingException.MalformedPayload("Missing 'sid'")
        val initiatorDeviceId = extractString(trimmed, "id") ?: throw PairingException.MalformedPayload("Missing 'id'")
        val initiatorPublicKeyBase64 = extractString(trimmed, "pk") ?: throw PairingException.MalformedPayload("Missing 'pk'")
        val initiatorDisplayName = extractString(trimmed, "name") ?: throw PairingException.MalformedPayload("Missing 'name'")
        val initiatorNonceBase64 = extractString(trimmed, "nonce") ?: throw PairingException.MalformedPayload("Missing 'nonce'")
        val expiresAtEpochMillis = extractLong(trimmed, "exp") ?: throw PairingException.MalformedPayload("Missing or invalid 'exp'")

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

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun extractString(json: String, key: String): String? {
      val pattern = "\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"".toRegex()
      val match = pattern.find(json) ?: return null
      return match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
    }

    private fun extractInt(json: String, key: String): Int? {
      val pattern = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
      return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractLong(json: String, key: String): Long? {
      val pattern = "\"$key\"\\s*:\\s*(-?\\d+)".toRegex()
      return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
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
)

data class PairingResponse(
  val protocolVersion: Int,
  val sessionId: String,
  val initiatorDeviceId: String,
  val initiatorSignatureBase64: String,
)

data class PairingConfirmation(
  val protocolVersion: Int,
  val sessionId: String,
  val responderDeviceId: String,
  val confirmationSignatureBase64: String,
)

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

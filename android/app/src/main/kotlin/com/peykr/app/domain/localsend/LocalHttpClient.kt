package com.peykr.app.domain.localsend

import android.util.Log
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.transfer.DiscoveredDevice
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferDirection
import com.peykr.app.domain.transfer.TransferFileItem
import com.peykr.app.domain.transfer.TransferSession
import com.peykr.app.domain.transfer.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DirectSendAck(
  val success: Boolean,
  val messageId: String? = null,
  val ackPayload: String? = null,
  val ackSignature: String? = null,
  val errorMessage: String? = null,
)

open class LocalHttpClient(
  private val localDeviceId: String,
  private val localDisplayName: String,
  private val connectTimeoutMs: Int = 4000,
  private val readTimeoutMs: Int = 10000,
) {

  open suspend fun sendDirectMessage(
    target: DiscoveredDevice,
    message: Message,
    encryptedPayload: com.peykr.app.core.security.EncryptedRatchetMessage? = null,
  ): Result<DirectSendAck> = withContext(Dispatchers.IO) {
    runCatching {
      if (encryptedPayload == null) {
        throw SecurityException("Refusing to transmit unencrypted message over network: end-to-end encryption is required")
      }

      val url = URL("http://${target.ipAddress}:${target.port}/api/peykr/v1/message/direct")
      val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
      }

      val json = JSONObject().apply {
        put("id", message.id)
        put("conversationId", message.conversationId)
        put("senderId", message.senderId)
        put("recipientId", message.recipientId)
        put("timestamp", message.timestamp)
        put("encryptedPayload", JSONObject().apply {
          put("senderDhPublicKey", encryptedPayload.header.senderDhPublicKeyBase64)
          put("sequenceNumber", encryptedPayload.header.sequenceNumber)
          put("previousChainLength", encryptedPayload.header.previousChainLength)
          put("iv", encryptedPayload.ivBase64)
          put("ciphertextWithTag", encryptedPayload.ciphertextWithTagBase64)
        })
      }

      val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
      connection.outputStream.use { it.write(bytes) }

      val responseCode = connection.responseCode
      if (responseCode == 200) {
        val respBody = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()
        val respJson = JSONObject(respBody)
        val status = respJson.optString("status")
        val ackMsgId = respJson.optString("messageId")
        val ackPayload = respJson.optString("ackPayload")
        val ackSignature = respJson.optString("ackSignature")
        DirectSendAck(
          success = (status == "DELIVERED"),
          messageId = ackMsgId.ifEmpty { null },
          ackPayload = ackPayload.ifEmpty { null },
          ackSignature = ackSignature.ifEmpty { null },
        )
      } else {
        val errBody = runCatching { connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } }.getOrNull()
        connection.disconnect()
        DirectSendAck(
          success = false,
          errorMessage = "HTTP $responseCode: $errBody",
        )
      }
    }
  }

  suspend fun sendPairingExchange(
    targetIp: String,
    targetPort: Int,
    payload: com.peykr.app.domain.pairing.PairingExchangePayload,
  ): Result<com.peykr.app.domain.pairing.PairingApprovalPayload> = withContext(Dispatchers.IO) {
    runCatching {
      val url = URL("http://$targetIp:$targetPort/api/peykr/v1/pair/exchange")
      val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
      }

      val json = JSONObject().apply {
        put("v", payload.version)
        put("sid", payload.sessionId)
        put("id", payload.responderDeviceId)
        put("pk", payload.responderPublicKeyBase64)
        put("name", payload.responderDisplayName)
        put("nonce", payload.responderNonceBase64)
        put("sig", payload.responderSignatureBase64)
        if (payload.responderIp != null) {
          put("ip", payload.responderIp)
          put("port", payload.responderPort)
        }
      }

      val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
      connection.outputStream.use { it.write(bytes) }

      val responseCode = connection.responseCode
      if (responseCode == 200) {
        val respBody = connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        connection.disconnect()
        val respJson = JSONObject(respBody)
        com.peykr.app.domain.pairing.PairingApprovalPayload(
          version = respJson.optInt("v", 1),
          sessionId = payload.sessionId,
          initiatorDeviceId = respJson.getString("initiatorDeviceId"),
          initiatorPublicKeyBase64 = respJson.getString("initiatorPublicKeyBase64"),
          initiatorDisplayName = respJson.optString("initiatorDisplayName", "Peykr Cihazı"),
          initiatorSignatureBase64 = respJson.optString("signature", ""),
          status = "PAIRED",
        )
      } else {
        val errBody = runCatching { connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } }.getOrNull()
        connection.disconnect()
        throw IllegalStateException("Pairing exchange failed HTTP $responseCode: $errBody")
      }
    }
  }

  suspend fun sendFiles(
    target: DiscoveredDevice,
    files: List<File>,
    onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> },
  ): Result<TransferSession> = withContext(Dispatchers.IO) {
    runCatching {
      val sessionId = UUID.randomUUID().toString()
      val fileItems = files.map { f ->
        val fileBytes = f.readBytes()
        val hash = FileTransferEngine.computeSha256(fileBytes)
        TransferFileItem(
          id = UUID.randomUUID().toString(),
          fileName = FileTransferEngine.sanitizeFileName(f.name),
          sizeBytes = f.length(),
          mimeType = "application/octet-stream",
          sha256Checksum = hash,
          localPath = f.absolutePath,
        )
      }

      val totalBytes = fileItems.sumOf { it.sizeBytes }

      // 1. Send Prepare Request
      val prepareUrl = URL("http://${target.ipAddress}:${target.port}/api/peykr/v1/transfer/prepare")
      val prepareConn = (prepareUrl.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
      }

      val proposalJson = JSONObject().apply {
        put("sessionId", sessionId)
        put("senderDeviceId", localDeviceId)
        put("senderDisplayName", localDisplayName)
        put("senderModel", android.os.Build.MODEL)
        put("files", JSONArray().apply {
          fileItems.forEach { fi ->
            put(JSONObject().apply {
              put("id", fi.id)
              put("fileName", fi.fileName)
              put("sizeBytes", fi.sizeBytes)
              put("mimeType", fi.mimeType)
              put("sha256Checksum", fi.sha256Checksum)
            })
          }
        })
      }

      prepareConn.outputStream.use { it.write(proposalJson.toString().toByteArray(StandardCharsets.UTF_8)) }
      val prepareCode = prepareConn.responseCode
      prepareConn.disconnect()
      check(prepareCode == 200) { "Transfer proposal rejected or failed with HTTP $prepareCode" }

      var totalBytesSent = 0L

      // 2. Stream Chunks for each file
      for (fileItem in fileItems) {
        val file = files.first { FileTransferEngine.sanitizeFileName(it.name) == fileItem.fileName }
        val buffer = ByteArray(CHUNK_SIZE)

        FileInputStream(file).use { fis ->
          var bytesRead = fis.read(buffer)
          while (bytesRead != -1) {
            val chunkUrl = URL("http://${target.ipAddress}:${target.port}/api/peykr/v1/transfer/chunk?sessionId=$sessionId&fileId=${fileItem.id}")
            val chunkConn = (chunkUrl.openConnection() as HttpURLConnection).apply {
              requestMethod = "POST"
              connectTimeout = connectTimeoutMs
              readTimeout = readTimeoutMs
              doOutput = true
              setRequestProperty("Content-Type", "application/octet-stream")
            }

            chunkConn.outputStream.use { it.write(buffer, 0, bytesRead) }
            val chunkCode = chunkConn.responseCode
            chunkConn.disconnect()
            check(chunkCode == 200) { "Chunk write failed with HTTP $chunkCode" }

            totalBytesSent += bytesRead
            onProgress(totalBytesSent, totalBytes)
            bytesRead = fis.read(buffer)
          }
        }

        // 3. Finalize file and verify checksum
        val finalizeUrl = URL("http://${target.ipAddress}:${target.port}/api/peykr/v1/transfer/finalize?sessionId=$sessionId&fileId=${fileItem.id}")
        val finalizeConn = (finalizeUrl.openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          connectTimeout = connectTimeoutMs
          readTimeout = readTimeoutMs
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
        }
        val finalizeCode = finalizeConn.responseCode
        finalizeConn.disconnect()
        check(finalizeCode == 200) { "Finalize failed with HTTP $finalizeCode" }
      }

      TransferSession(
        sessionId = sessionId,
        remoteDeviceId = target.deviceId,
        remoteDisplayName = target.displayName,
        direction = TransferDirection.SEND,
        files = fileItems,
        status = TransferStatus.COMPLETED,
        totalBytes = totalBytes,
        bytesTransferred = totalBytes,
        progressPercent = 100f,
      )
    }
  }

  companion object {
    private const val TAG = "LocalHttpClient"
    const val CHUNK_SIZE = 64 * 1024 // 64 KB
  }
}

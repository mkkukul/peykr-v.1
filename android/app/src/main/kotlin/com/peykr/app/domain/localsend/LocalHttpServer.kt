package com.peykr.app.domain.localsend

import android.util.Log
import com.peykr.app.core.security.EncryptedRatchetMessage
import com.peykr.app.core.security.RatchetHeader
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferFileItem
import com.peykr.app.domain.transfer.TransferProposal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class LocalHttpServer(
  val port: Int = DEFAULT_PORT,
  private val localDeviceId: String,
  private val localDisplayName: String,
  private val localFingerprint: String,
  private val fileTransferEngine: FileTransferEngine,
  private val messagingEngine: MessagingEngine? = null,
  private val contactRepository: TrustedContactRepository? = null,
  private val onDirectMessageReceived: ((Message) -> Unit)? = null,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  var boundPort: Int = port
    private set

  fun start(): Int {
    if (serverSocket != null && !serverSocket!!.isClosed) return boundPort

    var candidatePort = port
    var bound = false
    while (!bound && candidatePort < port + 50) {
      try {
        serverSocket = ServerSocket(candidatePort)
        boundPort = candidatePort
        bound = true
        Log.i(TAG, "Peykr Local HTTP server bound to port $boundPort")
      } catch (e: Exception) {
        candidatePort++
      }
    }

    check(bound) { "Could not bind Local HTTP server to any port in range" }

    serverJob = scope.launch {
      while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
        try {
          val client = serverSocket!!.accept()
          scope.launch { handleClient(client) }
        } catch (e: Exception) {
          if (!isActive) break
        }
      }
    }

    return boundPort
  }

  fun stop() {
    serverJob?.cancel()
    runCatching { serverSocket?.close() }
    serverSocket = null
  }

  private suspend fun handleClient(socket: Socket) {
    socket.use { s ->
      try {
        val inputStream = s.getInputStream()
        val outputStream = s.getOutputStream()

        // Read request line and headers
        val headerLines = mutableListOf<String>()
        var line = readLine(inputStream)
        while (line.isNotEmpty()) {
          headerLines.add(line)
          line = readLine(inputStream)
        }

        if (headerLines.isEmpty()) return

        val requestLine = headerLines[0].split(" ")
        if (requestLine.size < 2) return

        val method = requestLine[0].uppercase()
        val fullPath = requestLine[1]
        val path = fullPath.substringBefore("?")
        val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""
        val queryParams = parseQueryParams(queryString)

        val headers = headerLines.drop(1).associate {
          val parts = it.split(":", limit = 2)
          if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else "" to ""
        }
        val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L

        when {
          method == "GET" && path == "/api/peykr/v1/info" -> {
            val json = JSONObject().apply {
              put("deviceId", localDeviceId)
              put("displayName", localDisplayName)
              put("fingerprint", localFingerprint)
              put("version", "1.0")
            }
            sendJsonResponse(outputStream, 200, json.toString())
          }

          method == "POST" && path == "/api/peykr/v1/transfer/prepare" -> {
            val body = readBodyString(inputStream, contentLength)
            val json = JSONObject(body)
            val proposal = parseTransferProposal(json)
            val session = fileTransferEngine.receiveProposal(proposal)

            val responseJson = JSONObject().apply {
              put("sessionId", session.sessionId)
              put("status", session.status.name)
            }
            sendJsonResponse(outputStream, 200, responseJson.toString())
          }

          method == "POST" && path == "/api/peykr/v1/transfer/chunk" -> {
            val sessionId = queryParams["sessionId"] ?: ""
            val fileId = queryParams["fileId"] ?: ""
            if (sessionId.isBlank() || fileId.isBlank()) {
              sendJsonResponse(outputStream, 400, """{"error":"Missing sessionId or fileId"}""")
              return
            }

            val chunkBytes = readBodyBytes(inputStream, contentLength)
            val writeResult = fileTransferEngine.writeIncomingChunk(sessionId, fileId, chunkBytes)
            if (writeResult.isSuccess) {
              sendJsonResponse(outputStream, 200, """{"status":"OK"}""")
            } else {
              sendJsonResponse(outputStream, 400, """{"error":"${writeResult.exceptionOrNull()?.message}"}""")
            }
          }

          method == "POST" && path == "/api/peykr/v1/transfer/finalize" -> {
            val sessionId = queryParams["sessionId"] ?: ""
            val fileId = queryParams["fileId"] ?: ""
            val finalizeResult = fileTransferEngine.finalizeIncomingFile(sessionId, fileId)
            val verified = finalizeResult.getOrDefault(false)

            val responseJson = JSONObject().apply {
              put("verified", verified)
              put("status", if (verified) "COMPLETED" else "FAILED")
            }
            sendJsonResponse(outputStream, if (verified) 200 else 400, responseJson.toString())
          }

          method == "POST" && path == "/api/peykr/v1/message/direct" -> {
            val body = readBodyString(inputStream, contentLength)
            val json = JSONObject(body)
            val senderId = json.optString("senderId", "")
            val recipientId = json.optString("recipientId", "")

            // 1. Authenticate sender: sender must be in contact repository and PAIRED
            if (contactRepository != null) {
              val contact = contactRepository.getById(senderId)
              if (contact == null || contact.pairingStatus != PairingStatus.PAIRED) {
                Log.w(TAG, "Rejected message from unauthenticated/unpaired device: $senderId")
                sendJsonResponse(outputStream, 403, """{"error":"Forbidden: sender is not an authenticated paired contact"}""")
                return
              }
            }

            // 2. Reject cleartext: end-to-end encryption is strictly required
            val hasEncrypted = json.has("encryptedPayload") && !json.isNull("encryptedPayload")
            if (!hasEncrypted) {
              Log.w(TAG, "Rejected plaintext message from sender: $senderId. Cleartext transport is forbidden.")
              sendJsonResponse(outputStream, 400, """{"error":"Bad Request: plaintext transport is forbidden; encryptedPayload required"}""")
              return
            }

            // 3. Decrypt and verify end-to-end cryptographic integrity
            val encJson = json.getJSONObject("encryptedPayload")
            val encMsg = EncryptedRatchetMessage(
              header = RatchetHeader(
                senderDhPublicKeyBase64 = encJson.getString("senderDhPublicKey"),
                sequenceNumber = encJson.getInt("sequenceNumber"),
                previousChainLength = encJson.getInt("previousChainLength"),
              ),
              ivBase64 = encJson.getString("iv"),
              ciphertextWithTagBase64 = encJson.getString("ciphertextWithTag"),
            )

            val senderPubKey = contactRepository?.getById(senderId)?.publicIdentity ?: encJson.getString("senderDhPublicKey")
            var decryptedMsg: Message? = null
            if (messagingEngine != null) {
              val decryptResult = messagingEngine.receiveEncryptedTextMessage(
                conversationId = "conv_$senderId",
                senderId = senderId,
                encryptedMessage = encMsg,
                senderPublicKeyBase64 = senderPubKey
              )
              if (decryptResult.isFailure) {
                val err = decryptResult.exceptionOrNull()?.message ?: "Cryptographic mismatch"
                Log.e(TAG, "Decryption failed for direct message from $senderId: $err")
                sendJsonResponse(outputStream, 400, """{"error":"Decryption failed: cryptographic integrity check failed ($err)"}""")
                return
              }
              decryptedMsg = decryptResult.getOrThrow()
            }

            if (decryptedMsg != null) {
              onDirectMessageReceived?.invoke(decryptedMsg)
              sendJsonResponse(outputStream, 200, """{"status":"DELIVERED","messageId":"${decryptedMsg.id}"}""")
            } else {
              sendJsonResponse(outputStream, 500, """{"error":"Messaging engine not initialized"}""")
            }
          }

          else -> {
            sendJsonResponse(outputStream, 404, """{"error":"Not Found"}""")
          }
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error handling HTTP client: ${e.message}")
      }
    }
  }

  private fun readLine(input: InputStream): String {
    val sb = StringBuilder()
    var c: Int
    while (input.read().also { c = it } != -1) {
      if (c == '\r'.code) {
        val next = input.read()
        if (next == '\n'.code || next == -1) break
        sb.append(c.toChar())
        sb.append(next.toChar())
      } else if (c == '\n'.code) {
        break
      } else {
        sb.append(c.toChar())
      }
    }
    return sb.toString()
  }

  private fun readBodyString(input: InputStream, length: Long): String {
    val bytes = readBodyBytes(input, length)
    return String(bytes, StandardCharsets.UTF_8)
  }

  private fun readBodyBytes(input: InputStream, length: Long): ByteArray {
    val buffer = ByteArrayOutputStream()
    val temp = ByteArray(8192)
    var remaining = length
    while (remaining > 0) {
      val read = input.read(temp, 0, minOf(remaining.toInt(), temp.size))
      if (read == -1) break
      buffer.write(temp, 0, read)
      remaining -= read
    }
    return buffer.toByteArray()
  }

  private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    return query.split("&").associate {
      val parts = it.split("=", limit = 2)
      val k = URLDecoder.decode(parts[0], "UTF-8")
      val v = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
      k to v
    }
  }

  private fun parseTransferProposal(json: JSONObject): TransferProposal {
    val filesJson = json.getJSONArray("files")
    val files = mutableListOf<TransferFileItem>()
    for (i in 0 until filesJson.length()) {
      val f = filesJson.getJSONObject(i)
      files.add(
        TransferFileItem(
          id = f.getString("id"),
          fileName = f.getString("fileName"),
          sizeBytes = f.getLong("sizeBytes"),
          mimeType = f.optString("mimeType", "application/octet-stream"),
          sha256Checksum = f.getString("sha256Checksum"),
        )
      )
    }

    return TransferProposal(
      sessionId = json.getString("sessionId"),
      senderDeviceId = json.getString("senderDeviceId"),
      senderDisplayName = json.getString("senderDisplayName"),
      senderModel = json.optString("senderModel", "Android"),
      files = files,
      transferToken = json.optString("transferToken", ""),
    )
  }

  private fun sendJsonResponse(output: OutputStream, statusCode: Int, json: String) {
    val bytes = json.toByteArray(StandardCharsets.UTF_8)
    val statusText = when (statusCode) {
      200 -> "OK"
      400 -> "Bad Request"
      404 -> "Not Found"
      else -> "Error"
    }
    val responseHeader = "HTTP/1.1 $statusCode $statusText\r\n" +
      "Content-Type: application/json; charset=utf-8\r\n" +
      "Content-Length: ${bytes.size}\r\n" +
      "Connection: close\r\n\r\n"

    output.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
    output.write(bytes)
    output.flush()
  }

  companion object {
    private const val TAG = "LocalHttpServer"
    const val DEFAULT_PORT = 53317
  }
}

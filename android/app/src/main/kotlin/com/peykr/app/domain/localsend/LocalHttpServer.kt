package com.peykr.app.domain.localsend

import android.util.Log
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.getOrNull
import com.peykr.app.core.security.EncryptedRatchetMessage
import com.peykr.app.core.security.RatchetHeader
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferFileItem
import com.peykr.app.domain.transfer.TransferProposal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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
  private val deviceIdentity: DeviceIdentity? = null,
  private val onDirectMessageReceived: ((Message) -> Unit)? = null,
  var onPairingCompleted: ((com.peykr.app.domain.contacts.TrustedContact) -> Unit)? = null,
  private val localNetworkDiscovery: LocalNetworkDiscovery? = null,
  private val messagingRepository: MessagingRepository? = null,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

  var activeQrPayload: com.peykr.app.domain.pairing.PairingQrPayload? = null

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
        serverSocket = ServerSocket(candidatePort, 50, java.net.InetAddress.getByName("0.0.0.0"))
        boundPort = candidatePort
        bound = true
        Log.i(TAG, "Peykr Local HTTP server bound to port $boundPort on 0.0.0.0")
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

          method == "GET" && path == "/api/peykr/v1/pair/qr" -> {
            val qr = activeQrPayload
            if (qr != null) {
              sendJsonResponse(outputStream, 200, qr.serialize())
            } else {
              sendJsonResponse(outputStream, 404, """{"error":"No active pairing QR available"}""")
            }
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

          method == "POST" && path == "/api/peykr/v1/pair/exchange" -> {
            val body = readBodyString(inputStream, contentLength)
            val json = JSONObject(body)
            val responderId = json.optString("id").ifEmpty { json.optString("responderDeviceId", "") }
            val responderPk = json.optString("pk").ifEmpty { json.optString("responderPublicKeyBase64", "") }
            val responderName = json.optString("name").ifEmpty { json.optString("responderDisplayName", "Peykr Cihazı") }
            val responderSig = json.optString("sig").ifEmpty { json.optString("responderSignatureBase64", "") }
            val responderNonce = json.optString("nonce").ifEmpty { json.optString("responderNonceBase64", "") }

            val remoteIdentity = com.peykr.app.domain.pairing.RemoteDeviceIdentity(
              deviceId = responderId,
              publicKeyBase64 = responderPk,
              displayName = responderName
            )
            if (!remoteIdentity.verifyDeviceId()) {
              Log.w(TAG, "Rejected pairing exchange: public key digest mismatch for $responderId")
              sendJsonResponse(outputStream, 400, """{"error":"Public key digest mismatch"}""")
              return
            }

            // Save contact as PAIRED directly upon verified exchange
            val repo = contactRepository
            if (repo != null) {
              val contact = com.peykr.app.domain.contacts.TrustedContact(
                id = responderId,
                displayName = responderName,
                publicIdentity = responderPk,
                pairingStatus = PairingStatus.PAIRED,
                createdAtEpochMillis = System.currentTimeMillis()
              )
              val existing = repo.getById(responderId)
              if (existing == null) {
                repo.add(contact)
              } else {
                repo.update(contact)
              }
              onPairingCompleted?.invoke(contact)
              Log.i(TAG, "Successfully paired with device over LAN: $responderName ($responderId)")
            }

            val responderIp = json.optString("ip").ifEmpty { socket.inetAddress?.hostAddress }
            val responderPort = json.optInt("port", 53317)
            if (!responderIp.isNullOrBlank()) {
              localNetworkDiscovery?.addVirtualDiscoveredDevice(
                com.peykr.app.domain.transfer.DiscoveredDevice(
                  deviceId = responderId,
                  displayName = responderName,
                  deviceModel = "Android",
                  ipAddress = responderIp,
                  port = responderPort,
                  fingerprint = responderPk,
                  lastSeenEpochMillis = System.currentTimeMillis()
                )
              )
              localNetworkDiscovery?.cachePeerAddress(responderId, responderIp, responderPort)
            }

            // Sign initiator approval
            val approvalTranscript = "$responderNonce:$localDeviceId:APPROVED".toByteArray(StandardCharsets.UTF_8)
            val approvalSigBytes = deviceIdentity?.sign(approvalTranscript)?.getOrNull()
            val approvalSigB64 = approvalSigBytes?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: ""

            val respJson = JSONObject().apply {
              put("status", "PAIRED")
              put("initiatorDeviceId", localDeviceId)
              put("initiatorPublicKeyBase64", localFingerprint)
              put("initiatorDisplayName", localDisplayName)
              put("signature", approvalSigB64)
            }
            sendJsonResponse(outputStream, 200, respJson.toString())
          }

          method == "POST" && path == "/api/peykr/v1/message/direct" -> {
            val body = readBodyString(inputStream, contentLength)
            val json = JSONObject(body)
            val senderId = json.optString("senderId", "")
            val recipientId = json.optString("recipientId", "")

            // 1. Authenticate sender: sender must be in contact repository and PAIRED
            val repo = contactRepository
            val matchedContact = if (repo != null) {
              val direct = repo.getById(senderId)
              if (direct != null && direct.pairingStatus == PairingStatus.PAIRED) {
                direct
              } else {
                runCatching {
                  val all = repo.contacts.first()
                  all.firstOrNull { c ->
                    (c.id.equals(senderId, ignoreCase = true) || c.publicIdentity == senderId) &&
                      c.pairingStatus == PairingStatus.PAIRED
                  }
                }.getOrNull()
              }
            } else null

            if (repo != null && matchedContact == null) {
              Log.w(TAG, "Rejected message from unauthenticated/unpaired device: $senderId")
              sendJsonResponse(outputStream, 403, """{"error":"Forbidden: sender is not an authenticated paired contact"}""")
              return
            }

            val resolvedSenderId = matchedContact?.id ?: senderId

            // 1.5 Idempotency check: if message with this ID was already received and persisted,
            // return DELIVERED ACK immediately without running the ratchet decryption step (avoids replay error)
            val wireMessageId = json.optString("id").ifEmpty { null }
            if (wireMessageId != null && messagingRepository != null) {
              val existing = messagingRepository.getMessageById(wireMessageId)
              if (existing != null) {
                val ackTimestamp = System.currentTimeMillis()
                val ackPayload = "${existing.id}:DELIVERED:$ackTimestamp"
                val sigBytes = deviceIdentity?.sign(ackPayload.toByteArray(StandardCharsets.UTF_8))?.getOrNull()
                val sigB64 = sigBytes?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: ""

                val responseJson = JSONObject().apply {
                  put("status", "DELIVERED")
                  put("messageId", existing.id)
                  put("ackPayload", ackPayload)
                  put("ackSignature", sigB64)
                }
                sendJsonResponse(outputStream, 200, responseJson.toString())
                return
              }
            }

            // 2. Reject cleartext: end-to-end encryption is strictly required
            val hasEncrypted = json.has("encryptedPayload") && !json.isNull("encryptedPayload")
            if (!hasEncrypted) {
              Log.w(TAG, "Rejected plaintext message from sender: $resolvedSenderId. Cleartext transport is forbidden.")
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

            val senderPubKey = matchedContact?.publicIdentity ?: encJson.getString("senderDhPublicKey")
            var decryptedMsg: Message? = null
            if (messagingEngine != null) {
              val decryptResult = messagingEngine.receiveEncryptedTextMessage(
                conversationId = "conv_$resolvedSenderId",
                senderId = resolvedSenderId,
                encryptedMessage = encMsg,
                senderPublicKeyBase64 = senderPubKey,
                messageId = wireMessageId,
              )
              if (decryptResult.isFailure) {
                val err = decryptResult.exceptionOrNull()?.message ?: "Cryptographic mismatch"
                Log.e(TAG, "Decryption failed for direct message from $resolvedSenderId: $err")
                sendJsonResponse(outputStream, 400, """{"error":"Decryption failed: cryptographic integrity check failed ($err)"}""")
                return
              }
              decryptedMsg = decryptResult.getOrThrow()
            }

            if (decryptedMsg != null) {
              onDirectMessageReceived?.invoke(decryptedMsg)
              val clientIp = socket.inetAddress?.hostAddress
              if (!clientIp.isNullOrBlank()) {
                localNetworkDiscovery?.addVirtualDiscoveredDevice(
                  com.peykr.app.domain.transfer.DiscoveredDevice(
                    deviceId = resolvedSenderId,
                    displayName = matchedContact?.displayName ?: "Peykr Cihazı",
                    deviceModel = "Android",
                    ipAddress = clientIp,
                    port = 53317,
                    fingerprint = senderPubKey,
                    lastSeenEpochMillis = System.currentTimeMillis()
                  )
                )
                localNetworkDiscovery?.cachePeerAddress(resolvedSenderId, clientIp, 53317)
              }

              val ackTimestamp = System.currentTimeMillis()
              val ackPayload = "${decryptedMsg.id}:DELIVERED:$ackTimestamp"
              val sigBytes = deviceIdentity?.sign(ackPayload.toByteArray(StandardCharsets.UTF_8))?.getOrNull()
              val sigB64 = sigBytes?.let { java.util.Base64.getEncoder().encodeToString(it) } ?: ""

              val responseJson = JSONObject().apply {
                put("status", "DELIVERED")
                put("messageId", decryptedMsg.id)
                put("ackPayload", ackPayload)
                put("ackSignature", sigB64)
              }
              sendJsonResponse(outputStream, 200, responseJson.toString())
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

package com.peykr.app.domain.transfer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FileTransferEngine(
  private val transferCacheDir: File,
  private val completedDownloadsDir: File,
) {

  private val _activeSessions = MutableStateFlow<Map<String, TransferSession>>(emptyMap())
  val activeSessions: StateFlow<Map<String, TransferSession>> = _activeSessions.asStateFlow()

  private val activeFileOutputStreams = ConcurrentHashMap<String, FileOutputStream>()
  private val activeFileDigests = ConcurrentHashMap<String, MessageDigest>()

  init {
    transferCacheDir.mkdirs()
    completedDownloadsDir.mkdirs()
  }

  fun createOutgoingSession(
    targetDevice: DiscoveredDevice,
    files: List<TransferFileItem>,
  ): TransferSession {
    val sessionId = UUID.randomUUID().toString()
    val sanitizedFiles = files.map { it.copy(fileName = sanitizeFileName(it.fileName)) }
    val session = TransferSession(
      sessionId = sessionId,
      remoteDeviceId = targetDevice.deviceId,
      remoteDisplayName = targetDevice.displayName,
      direction = TransferDirection.SEND,
      files = sanitizedFiles,
      status = TransferStatus.WAITING_FOR_RECEIVER_APPROVAL,
    )
    updateSession(session)
    return session
  }

  fun receiveProposal(proposal: TransferProposal): TransferSession {
    val sanitizedFiles = proposal.files.map { it.copy(fileName = sanitizeFileName(it.fileName)) }
    val session = TransferSession(
      sessionId = proposal.sessionId,
      remoteDeviceId = proposal.senderDeviceId,
      remoteDisplayName = proposal.senderDisplayName,
      direction = TransferDirection.RECEIVE,
      files = sanitizedFiles,
      status = TransferStatus.WAITING_FOR_RECEIVER_APPROVAL,
    )
    updateSession(session)
    return session
  }

  fun acceptTransfer(sessionId: String): Result<Unit> = runCatching {
    val current = getSession(sessionId) ?: error("Transfer session $sessionId not found")
    check(current.status == TransferStatus.WAITING_FOR_RECEIVER_APPROVAL) { "Invalid session status" }

    val updated = current.copy(status = TransferStatus.TRANSFERRING)
    updateSession(updated)
  }

  fun rejectTransfer(sessionId: String): Result<Unit> = runCatching {
    val current = getSession(sessionId) ?: return@runCatching
    val updated = current.copy(status = TransferStatus.REJECTED_BY_RECEIVER)
    updateSession(updated)
    cleanupSessionFiles(sessionId)
  }

  fun writeIncomingChunk(sessionId: String, fileId: String, chunk: ByteArray): Result<Unit> = runCatching {
    val session = getSession(sessionId) ?: error("Transfer session not found")
    check(session.status == TransferStatus.TRANSFERRING) { "Session is not in transferring state" }

    val fileKey = "${sessionId}_$fileId"
    val fos = activeFileOutputStreams.computeIfAbsent(fileKey) {
      val tempFile = File(transferCacheDir, "$fileKey.tmp")
      FileOutputStream(tempFile, true)
    }
    val digest = activeFileDigests.computeIfAbsent(fileKey) {
      MessageDigest.getInstance("SHA-256")
    }

    fos.write(chunk)
    digest.update(chunk)

    // Update progress
    val newTransferred = session.bytesTransferred + chunk.size
    val total = session.totalBytes.coerceAtLeast(1L)
    val percent = ((newTransferred.toFloat() / total.toFloat()) * 100f).coerceIn(0f, 100f)

    val updatedFiles = session.files.map { f ->
      if (f.id == fileId) f.copy(bytesTransferred = f.bytesTransferred + chunk.size) else f
    }

    updateSession(
      session.copy(
        bytesTransferred = newTransferred,
        progressPercent = percent,
        files = updatedFiles,
      )
    )
  }

  fun finalizeIncomingFile(sessionId: String, fileId: String): Result<Boolean> = runCatching {
    val session = getSession(sessionId) ?: error("Session not found")
    val fileItem = session.files.firstOrNull { it.id == fileId } ?: error("File item $fileId not found")

    val fileKey = "${sessionId}_$fileId"
    val fos = activeFileOutputStreams.remove(fileKey)
    fos?.flush()
    fos?.close()

    val digest = activeFileDigests.remove(fileKey)
    val computedHash = digest?.digest()?.let { bytesToHex(it) } ?: ""

    val tempFile = File(transferCacheDir, "$fileKey.tmp")

    // Verify SHA-256 integrity
    if (!computedHash.equals(fileItem.sha256Checksum, ignoreCase = true)) {
      tempFile.delete()
      updateSession(session.copy(status = TransferStatus.FAILED, errorMessage = "SHA-256 integrity mismatch on ${fileItem.fileName}"))
      return@runCatching false
    }

    // Atomic move to completed downloads directory
    val safeName = getUniqueDestinationFile(completedDownloadsDir, fileItem.fileName)
    val committed = tempFile.renameTo(safeName)
    check(committed) { "Failed to commit verified file to destination" }

    val updatedFiles = session.files.map { f ->
      if (f.id == fileId) f.copy(isCompleted = true, localPath = safeName.absolutePath) else f
    }

    val allDone = updatedFiles.all { it.isCompleted }
    val newStatus = if (allDone) TransferStatus.COMPLETED else TransferStatus.TRANSFERRING

    updateSession(
      session.copy(
        status = newStatus,
        files = updatedFiles,
      )
    )
    true
  }

  fun cancelTransfer(sessionId: String) {
    val session = getSession(sessionId) ?: return
    updateSession(session.copy(status = TransferStatus.CANCELLED))
    cleanupSessionFiles(sessionId)
  }

  private fun cleanupSessionFiles(sessionId: String) {
    val prefix = "${sessionId}_"
    activeFileOutputStreams.keys.filter { it.startsWith(prefix) }.forEach { key ->
      runCatching { activeFileOutputStreams.remove(key)?.close() }
      activeFileDigests.remove(key)
      File(transferCacheDir, "$key.tmp").delete()
    }
  }

  private fun updateSession(session: TransferSession) {
    val current = _activeSessions.value.toMutableMap()
    current[session.sessionId] = session
    _activeSessions.value = current
  }

  fun getSession(sessionId: String): TransferSession? = _activeSessions.value[sessionId]

  companion object {
    fun sanitizeFileName(rawName: String): String {
      val baseName = File(rawName).name
      return baseName.replace("[/\\\\:*?\"<>|]".toRegex(), "_")
        .replace("^[. ]+".toRegex(), "")
        .ifBlank { "downloaded_file" }
    }

    fun computeSha256(data: ByteArray): String {
      val md = MessageDigest.getInstance("SHA-256")
      return bytesToHex(md.digest(data))
    }

    private fun bytesToHex(bytes: ByteArray): String =
      bytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    private fun getUniqueDestinationFile(dir: File, fileName: String): File {
      var candidate = File(dir, fileName)
      if (!candidate.exists()) return candidate

      val dotIndex = fileName.lastIndexOf('.')
      val name = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
      val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""

      var counter = 1
      while (candidate.exists()) {
        candidate = File(dir, "${name}_$counter$ext")
        counter++
      }
      return candidate
    }
  }
}

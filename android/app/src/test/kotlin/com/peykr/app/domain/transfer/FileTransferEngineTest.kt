package com.peykr.app.domain.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class FileTransferEngineTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var cacheDir: File
  private lateinit var downloadsDir: File
  private lateinit var engine: FileTransferEngine

  @Before
  fun setUp() {
    cacheDir = tempFolder.newFolder("transfer_cache")
    downloadsDir = tempFolder.newFolder("downloads")
    engine = FileTransferEngine(cacheDir, downloadsDir)
  }

  @Test
  fun sanitizeFileName_protectsAgainstPathTraversal() {
    assertEquals("passwd", FileTransferEngine.sanitizeFileName("../../../../etc/passwd"))
    assertEquals("cmd.exe", FileTransferEngine.sanitizeFileName("C:\\Windows\\System32\\cmd.exe"))
    assertEquals("evil_file.apk", FileTransferEngine.sanitizeFileName("..\\..\\evil:file.apk"))
    assertEquals("downloaded_file", FileTransferEngine.sanitizeFileName("..."))
  }

  @Test
  fun receiverApproval_chunkTransfer_andIntegrityCommit_succeeds() {
    val content = "Peykr secure local file transfer test content payload".toByteArray(StandardCharsets.UTF_8)
    val hash = FileTransferEngine.computeSha256(content)

    val fileItem = TransferFileItem(
      id = "f1",
      fileName = "document.pdf",
      sizeBytes = content.size.toLong(),
      mimeType = "application/pdf",
      sha256Checksum = hash,
    )

    val proposal = TransferProposal(
      sessionId = "sess_100",
      senderDeviceId = "sender_dev",
      senderDisplayName = "Sender Device",
      senderModel = "Pixel 8",
      files = listOf(fileItem),
      transferToken = "token_abc",
    )

    val session = engine.receiveProposal(proposal)
    assertEquals(TransferStatus.WAITING_FOR_RECEIVER_APPROVAL, session.status)

    // Receiver approves
    engine.acceptTransfer(session.sessionId).getOrThrow()
    assertEquals(TransferStatus.TRANSFERRING, engine.getSession(session.sessionId)?.status)

    // Write chunk
    engine.writeIncomingChunk(session.sessionId, "f1", content).getOrThrow()

    // Finalize with valid hash
    val finalized = engine.finalizeIncomingFile(session.sessionId, "f1").getOrThrow()
    assertTrue(finalized)

    val updatedSession = engine.getSession(session.sessionId)
    assertEquals(TransferStatus.COMPLETED, updatedSession?.status)
    val savedFile = File(updatedSession?.files?.first()?.localPath ?: "")
    assertTrue(savedFile.exists())
    assertEquals(String(content), savedFile.readText())
  }

  @Test
  fun integrityMismatch_rejectsAndDeletesFile() {
    val content = "Corrupted content".toByteArray()
    val wrongHash = "0000000000000000000000000000000000000000000000000000000000000000"

    val fileItem = TransferFileItem(
      id = "f2",
      fileName = "image.png",
      sizeBytes = content.size.toLong(),
      mimeType = "image/png",
      sha256Checksum = wrongHash,
    )

    val proposal = TransferProposal(
      sessionId = "sess_200",
      senderDeviceId = "sender_dev",
      senderDisplayName = "Sender",
      senderModel = "Pixel",
      files = listOf(fileItem),
      transferToken = "tok",
    )

    val session = engine.receiveProposal(proposal)
    engine.acceptTransfer(session.sessionId).getOrThrow()
    engine.writeIncomingChunk(session.sessionId, "f2", content).getOrThrow()

    val finalized = engine.finalizeIncomingFile(session.sessionId, "f2").getOrThrow()
    assertFalse(finalized)
    assertEquals(TransferStatus.FAILED, engine.getSession(session.sessionId)?.status)
  }

  @Test
  fun receiverRejection_cancelsAndCleansUp() {
    val fileItem = TransferFileItem(
      id = "f3",
      fileName = "file.txt",
      sizeBytes = 100,
      mimeType = "text/plain",
      sha256Checksum = "abc",
    )
    val proposal = TransferProposal(
      sessionId = "sess_300",
      senderDeviceId = "sender_dev",
      senderDisplayName = "Sender",
      senderModel = "Pixel",
      files = listOf(fileItem),
      transferToken = "tok",
    )

    val session = engine.receiveProposal(proposal)
    engine.rejectTransfer(session.sessionId).getOrThrow()

    assertEquals(TransferStatus.REJECTED_BY_RECEIVER, engine.getSession(session.sessionId)?.status)
  }
}

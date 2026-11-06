package com.peykr.app.domain.localsend

import com.peykr.app.domain.transfer.DiscoveredDevice
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transfer.TransferDirection
import com.peykr.app.domain.transfer.TransferFileItem
import com.peykr.app.domain.transfer.TransferProposal
import com.peykr.app.domain.transfer.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.StandardCharsets

class LocalSendProtocolTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun testProposalAndChunkedChecksumVerification() {
    val cacheDir = tempFolder.newFolder("transfers_cache")
    val downloadsDir = tempFolder.newFolder("PeykrDownloads")
    val engine = FileTransferEngine(cacheDir, downloadsDir)

    val sampleContent = "Peykr LocalSend Secure Chunked Protocol Transfer Test Content 1234567890".toByteArray(StandardCharsets.UTF_8)
    val expectedSha256 = FileTransferEngine.computeSha256(sampleContent)

    val fileItem = TransferFileItem(
      id = "file-1",
      fileName = "secret_memo.txt",
      sizeBytes = sampleContent.size.toLong(),
      mimeType = "text/plain",
      sha256Checksum = expectedSha256,
    )

    val proposal = TransferProposal(
      sessionId = "session-123",
      senderDeviceId = "sender-laptop",
      senderDisplayName = "Ahmet Laptop",
      senderModel = "Linux",
      files = listOf(fileItem),
      transferToken = "token-xyz",
    )

    // 1. Receiver receives proposal
    val session = engine.receiveProposal(proposal)
    assertEquals(TransferStatus.WAITING_FOR_RECEIVER_APPROVAL, session.status)
    assertEquals(TransferDirection.RECEIVE, session.direction)

    // 2. Receiver approves transfer
    val acceptResult = engine.acceptTransfer("session-123")
    assertTrue(acceptResult.isSuccess)
    assertEquals(TransferStatus.TRANSFERRING, engine.getSession("session-123")?.status)

    // 3. Chunks streamed
    val chunk1 = sampleContent.copyOfRange(0, 20)
    val chunk2 = sampleContent.copyOfRange(20, sampleContent.size)
    engine.writeIncomingChunk("session-123", "file-1", chunk1)
    engine.writeIncomingChunk("session-123", "file-1", chunk2)

    // 4. Finalize and verify SHA-256 integrity
    val finalizeResult = engine.finalizeIncomingFile("session-123", "file-1")
    assertTrue(finalizeResult.isSuccess)
    assertTrue(finalizeResult.getOrThrow())

    val updatedSession = engine.getSession("session-123")
    assertEquals(TransferStatus.COMPLETED, updatedSession?.status)

    // Verify downloaded file exists in completed downloads dir
    val downloadedFile = File(downloadsDir, "secret_memo.txt")
    assertTrue("Verified file must exist in downloads folder", downloadedFile.exists())
    assertEquals(sampleContent.size.toLong(), downloadedFile.length())
  }

  @Test
  fun testChecksumMismatchRejectsFile() {
    val cacheDir = tempFolder.newFolder("transfers_cache_fail")
    val downloadsDir = tempFolder.newFolder("PeykrDownloads_fail")
    val engine = FileTransferEngine(cacheDir, downloadsDir)

    val fileItem = TransferFileItem(
      id = "file-corrupt",
      fileName = "corrupt.txt",
      sizeBytes = 10,
      mimeType = "text/plain",
      sha256Checksum = "0000000000000000000000000000000000000000000000000000000000000000", // Wrong hash
    )

    val proposal = TransferProposal(
      sessionId = "session-corrupt",
      senderDeviceId = "attacker",
      senderDisplayName = "Attacker",
      senderModel = "Unknown",
      files = listOf(fileItem),
      transferToken = "token-bad",
    )

    engine.receiveProposal(proposal)
    engine.acceptTransfer("session-corrupt")
    engine.writeIncomingChunk("session-corrupt", "file-corrupt", "CORRUPTED!".toByteArray())

    val finalizeResult = engine.finalizeIncomingFile("session-corrupt", "file-corrupt")
    assertFalse("Finalize must return false on checksum mismatch", finalizeResult.getOrDefault(true))

    val session = engine.getSession("session-corrupt")
    assertEquals(TransferStatus.FAILED, session?.status)
    assertFalse("Corrupted file must NOT be moved to downloads directory", File(downloadsDir, "corrupt.txt").exists())
  }
}

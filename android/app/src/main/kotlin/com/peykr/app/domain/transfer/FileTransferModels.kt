package com.peykr.app.domain.transfer

enum class TransferDirection {
  SEND,
  RECEIVE
}

enum class TransferStatus {
  DISCOVERING,
  PREPARING,
  WAITING_FOR_RECEIVER_APPROVAL,
  REJECTED_BY_RECEIVER,
  TRANSFERRING,
  VERIFYING_CHECKSUM,
  COMPLETED,
  FAILED,
  CANCELLED
}

data class TransferFileItem(
  val id: String,
  val fileName: String,
  val sizeBytes: Long,
  val mimeType: String,
  val sha256Checksum: String,
  val localPath: String? = null,
  val bytesTransferred: Long = 0L,
  val isCompleted: Boolean = false,
)

data class DiscoveredDevice(
  val deviceId: String,
  val displayName: String,
  val deviceModel: String,
  val ipAddress: String,
  val port: Int,
  val fingerprint: String,
  val lastSeenEpochMillis: Long = System.currentTimeMillis(),
)

data class TransferSession(
  val sessionId: String,
  val remoteDeviceId: String,
  val remoteDisplayName: String,
  val direction: TransferDirection,
  val files: List<TransferFileItem>,
  val status: TransferStatus = TransferStatus.PREPARING,
  val totalBytes: Long = files.sumOf { it.sizeBytes },
  val bytesTransferred: Long = 0L,
  val progressPercent: Float = 0f,
  val transferSpeedBytesPerSec: Long = 0L,
  val errorMessage: String? = null,
)

data class TransferProposal(
  val sessionId: String,
  val senderDeviceId: String,
  val senderDisplayName: String,
  val senderModel: String,
  val files: List<TransferFileItem>,
  val transferToken: String,
)

package com.peykr.app.domain.calling

import kotlinx.coroutines.flow.StateFlow

enum class CallType {
  VOICE,
  VIDEO
}

enum class CallDirection {
  OUTGOING,
  INCOMING
}

enum class CallState {
  IDLE,
  OUTGOING_DIALING,
  INCOMING_RINGING,
  CONNECTING,
  CONNECTED,
  RECONNECTING,
  ENDED,
  REJECTED,
  BUSY,
  FAILED
}

data class CallSession(
  val callId: String,
  val contactId: String,
  val contactDisplayName: String,
  val callType: CallType,
  val direction: CallDirection,
  val state: CallState = CallState.IDLE,
  val isMuted: Boolean = false,
  val isSpeakerOn: Boolean = false,
  val isVideoEnabled: Boolean = true,
  val isFrontCamera: Boolean = true,
  val startedAt: Long = System.currentTimeMillis(),
  val connectedAt: Long? = null,
  val durationSeconds: Long = 0L,
  val endReason: String? = null,
)

data class CallLogEntry(
  val id: String,
  val contactId: String,
  val contactDisplayName: String,
  val callType: CallType,
  val direction: CallDirection,
  val status: String,
  val startedAt: Long,
  val durationSeconds: Long,
)

sealed interface SignalingMessage {
  val callId: String

  data class Offer(
    override val callId: String,
    val sdp: String,
    val callType: CallType,
  ) : SignalingMessage

  data class Answer(
    override val callId: String,
    val sdp: String,
  ) : SignalingMessage

  data class IceCandidate(
    override val callId: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val candidate: String,
  ) : SignalingMessage

  data class Reject(
    override val callId: String,
    val reason: String = "Declined",
  ) : SignalingMessage

  data class Hangup(
    override val callId: String,
  ) : SignalingMessage
}

interface CallingEngine {
  val activeSession: StateFlow<CallSession?>

  suspend fun startCall(contactId: String, displayName: String, type: CallType): Result<CallSession>
  suspend fun acceptIncomingCall(callId: String): Result<CallSession>
  suspend fun rejectIncomingCall(callId: String, reason: String = "Declined"): Result<Unit>
  suspend fun endCall(callId: String): Result<Unit>
  fun toggleMute()
  fun toggleSpeaker()
  fun toggleVideo()
  fun switchCamera()
  fun handleIncomingSignaling(message: SignalingMessage)
}

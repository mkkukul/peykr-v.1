package com.peykr.app.domain.calling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class DefaultCallingEngine(
  private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
  private val onCallEnded: (CallLogEntry) -> Unit = {},
) : CallingEngine {

  private val _activeSession = MutableStateFlow<CallSession?>(null)
  override val activeSession: StateFlow<CallSession?> = _activeSession.asStateFlow()

  private var timerJob: Job? = null

  override suspend fun startCall(contactId: String, displayName: String, type: CallType): Result<CallSession> = runCatching {
    val callId = UUID.randomUUID().toString()
    val session = CallSession(
      callId = callId,
      contactId = contactId,
      contactDisplayName = displayName,
      callType = type,
      direction = CallDirection.OUTGOING,
      state = CallState.OUTGOING_DIALING,
    )
    _activeSession.value = session
    session
  }

  override suspend fun acceptIncomingCall(callId: String): Result<CallSession> = runCatching {
    val current = _activeSession.value ?: error("No active call to accept")
    check(current.callId == callId) { "Call ID mismatch" }

    val connected = current.copy(
      state = CallState.CONNECTED,
      connectedAt = System.currentTimeMillis(),
    )
    _activeSession.value = connected
    startDurationTimer()
    connected
  }

  override suspend fun rejectIncomingCall(callId: String, reason: String): Result<Unit> = runCatching {
    val current = _activeSession.value ?: return@runCatching
    if (current.callId == callId) {
      val ended = current.copy(state = CallState.REJECTED, endReason = reason)
      _activeSession.value = ended
      stopDurationTimer()
      recordLog(ended)
      _activeSession.value = null
    }
  }

  override suspend fun endCall(callId: String): Result<Unit> = runCatching {
    val current = _activeSession.value ?: return@runCatching
    if (current.callId == callId) {
      val ended = current.copy(state = CallState.ENDED)
      _activeSession.value = ended
      stopDurationTimer()
      recordLog(ended)
      _activeSession.value = null
    }
  }

  override fun toggleMute() {
    _activeSession.value?.let { current ->
      _activeSession.value = current.copy(isMuted = !current.isMuted)
    }
  }

  override fun toggleSpeaker() {
    _activeSession.value?.let { current ->
      _activeSession.value = current.copy(isSpeakerOn = !current.isSpeakerOn)
    }
  }

  override fun toggleVideo() {
    _activeSession.value?.let { current ->
      _activeSession.value = current.copy(isVideoEnabled = !current.isVideoEnabled)
    }
  }

  override fun switchCamera() {
    _activeSession.value?.let { current ->
      _activeSession.value = current.copy(isFrontCamera = !current.isFrontCamera)
    }
  }

  override fun handleIncomingSignaling(message: SignalingMessage) {
    when (message) {
      is SignalingMessage.Offer -> {
        if (_activeSession.value == null) {
          _activeSession.value = CallSession(
            callId = message.callId,
            contactId = "remote_peer",
            contactDisplayName = "Caller",
            callType = message.callType,
            direction = CallDirection.INCOMING,
            state = CallState.INCOMING_RINGING,
          )
        }
      }
      is SignalingMessage.Answer -> {
        val current = _activeSession.value
        if (current?.callId == message.callId) {
          val connected = current.copy(
            state = CallState.CONNECTED,
            connectedAt = System.currentTimeMillis(),
          )
          _activeSession.value = connected
          startDurationTimer()
        }
      }
      is SignalingMessage.Reject -> {
        val current = _activeSession.value
        if (current?.callId == message.callId) {
          val rejected = current.copy(state = CallState.REJECTED, endReason = message.reason)
          _activeSession.value = rejected
          stopDurationTimer()
          recordLog(rejected)
          _activeSession.value = null
        }
      }
      is SignalingMessage.Hangup -> {
        val current = _activeSession.value
        if (current?.callId == message.callId) {
          val ended = current.copy(state = CallState.ENDED)
          _activeSession.value = ended
          stopDurationTimer()
          recordLog(ended)
          _activeSession.value = null
        }
      }
      is SignalingMessage.IceCandidate -> {
        // ICE candidate received
      }
    }
  }

  private fun startDurationTimer() {
    timerJob?.cancel()
    timerJob = coroutineScope.launch {
      while (isActive) {
        delay(1000)
        _activeSession.value?.let { current ->
          if (current.state == CallState.CONNECTED) {
            val duration = current.durationSeconds + 1
            _activeSession.value = current.copy(durationSeconds = duration)
          }
        }
      }
    }
  }

  private fun stopDurationTimer() {
    timerJob?.cancel()
    timerJob = null
  }

  private fun recordLog(session: CallSession) {
    val status = when (session.state) {
      CallState.REJECTED -> "REJECTED"
      CallState.ENDED -> if (session.connectedAt != null) "COMPLETED" else "MISSED"
      else -> session.state.name
    }
    val log = CallLogEntry(
      id = session.callId,
      contactId = session.contactId,
      contactDisplayName = session.contactDisplayName,
      callType = session.callType,
      direction = session.direction,
      status = status,
      startedAt = session.startedAt,
      durationSeconds = session.durationSeconds,
    )
    onCallEnded(log)
  }
}

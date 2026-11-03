package com.peykr.app.domain.pairing

object PairingStateValidator {

  private val abortTransitions = setOf(
    PairingSessionState.EXPIRED,
    PairingSessionState.REJECTED,
    PairingSessionState.FAILED,
    PairingSessionState.INDETERMINATE,
  )

  private val allowedInitiatorTransitions: Map<PairingSessionState, Set<PairingSessionState>> = mapOf(
    PairingSessionState.CREATED to setOf(PairingSessionState.QR_READY) + abortTransitions,
    PairingSessionState.QR_READY to setOf(PairingSessionState.QR_READY, PairingSessionState.REQUEST_RECEIVED) + abortTransitions,
    PairingSessionState.REQUEST_RECEIVED to setOf(PairingSessionState.VERIFIED) + abortTransitions,
    PairingSessionState.VERIFIED to setOf(PairingSessionState.WAITING_FOR_LOCAL_APPROVAL) + abortTransitions,
    PairingSessionState.WAITING_FOR_LOCAL_APPROVAL to setOf(PairingSessionState.LOCAL_APPROVED) + abortTransitions,
    PairingSessionState.LOCAL_APPROVED to setOf(PairingSessionState.COMPLETION_SENT) + abortTransitions,
    PairingSessionState.COMPLETION_SENT to setOf(PairingSessionState.COMPLETION_RECEIVED, PairingSessionState.PAIRED) + abortTransitions,
    PairingSessionState.COMPLETION_RECEIVED to setOf(PairingSessionState.PAIRED) + abortTransitions,
    PairingSessionState.PAIRED to setOf(PairingSessionState.PAIRED),
    PairingSessionState.EXPIRED to emptySet(),
    PairingSessionState.REJECTED to emptySet(),
    PairingSessionState.FAILED to emptySet(),
    PairingSessionState.INDETERMINATE to emptySet(),
  )

  private val allowedResponderTransitions: Map<PairingSessionState, Set<PairingSessionState>> = mapOf(
    PairingSessionState.CREATED to setOf(PairingSessionState.REQUEST_RECEIVED, PairingSessionState.VERIFIED) + abortTransitions,
    PairingSessionState.REQUEST_RECEIVED to setOf(PairingSessionState.VERIFIED) + abortTransitions,
    PairingSessionState.VERIFIED to setOf(PairingSessionState.WAITING_FOR_LOCAL_APPROVAL) + abortTransitions,
    PairingSessionState.WAITING_FOR_LOCAL_APPROVAL to setOf(PairingSessionState.LOCAL_APPROVED) + abortTransitions,
    PairingSessionState.LOCAL_APPROVED to setOf(PairingSessionState.WAITING_FOR_REMOTE_RESPONSE) + abortTransitions,
    PairingSessionState.WAITING_FOR_REMOTE_RESPONSE to setOf(PairingSessionState.COMPLETION_RECEIVED, PairingSessionState.PAIRED) + abortTransitions,
    PairingSessionState.COMPLETION_RECEIVED to setOf(PairingSessionState.PAIRED) + abortTransitions,
    PairingSessionState.PAIRED to setOf(PairingSessionState.PAIRED),
    PairingSessionState.EXPIRED to emptySet(),
    PairingSessionState.REJECTED to emptySet(),
    PairingSessionState.FAILED to emptySet(),
    PairingSessionState.INDETERMINATE to emptySet(),
  )

  fun validateTransition(role: PairingRole, current: PairingSessionState, target: PairingSessionState) {
    if (current == target && current == PairingSessionState.PAIRED) {
      return
    }
    val allowed = when (role) {
      PairingRole.INITIATOR -> allowedInitiatorTransitions[current]
      PairingRole.RESPONDER -> allowedResponderTransitions[current]
    } ?: emptySet()

    if (target !in allowed) {
      throw PairingException.InvalidStateTransition(current, target)
    }
  }
}

package com.peykr.app.domain.calling

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCallingEngineTest {

  private lateinit var testScope: TestScope
  private var loggedEntry: CallLogEntry? = null
  private lateinit var engine: DefaultCallingEngine

  @Before
  fun setUp() {
    testScope = TestScope()
    loggedEntry = null
    engine = DefaultCallingEngine(
      coroutineScope = testScope,
      onCallEnded = { loggedEntry = it }
    )
  }

  @Test
  fun startCall_initiatesOutgoingDialing() = runTest {
    val session = engine.startCall("contact_1", "Alice", CallType.VOICE).getOrThrow()
    assertEquals(CallState.OUTGOING_DIALING, session.state)
    assertEquals("contact_1", session.contactId)
    assertEquals(CallDirection.OUTGOING, session.direction)
  }

  @Test
  fun incomingOffer_triggersRingingAndAcceptance() = runTest {
    engine.handleIncomingSignaling(SignalingMessage.Offer("call_99", "sdp_dummy", CallType.VIDEO))
    val current = engine.activeSession.value
    assertNotNull(current)
    assertEquals(CallState.INCOMING_RINGING, current?.state)

    val connected = engine.acceptIncomingCall("call_99").getOrThrow()
    assertEquals(CallState.CONNECTED, connected.state)
    assertNotNull(connected.connectedAt)

    // Advance time and check duration updates
    testScope.advanceTimeBy(3500)
    assertTrue("Duration should advance during active call", (engine.activeSession.value?.durationSeconds ?: 0L) >= 3)

    // End call
    engine.endCall("call_99").getOrThrow()
    assertNull(engine.activeSession.value)
    assertNotNull(loggedEntry)
    assertEquals("COMPLETED", loggedEntry?.status)
  }

  @Test
  fun incomingOffer_rejection_recordsMissedOrRejectedLog() = runTest {
    engine.handleIncomingSignaling(SignalingMessage.Offer("call_101", "sdp_dummy", CallType.VOICE))
    engine.rejectIncomingCall("call_101", "User busy").getOrThrow()

    assertNull(engine.activeSession.value)
    assertEquals("REJECTED", loggedEntry?.status)
  }

  @Test
  fun mediaControls_toggleMuteSpeakerAndVideo() = runTest {
    val session = engine.startCall("contact_2", "Bob", CallType.VIDEO).getOrThrow()
    assertFalse(session.isMuted)
    assertFalse(session.isSpeakerOn)
    assertTrue(session.isVideoEnabled)

    engine.toggleMute()
    assertTrue(engine.activeSession.value?.isMuted ?: false)

    engine.toggleSpeaker()
    assertTrue(engine.activeSession.value?.isSpeakerOn ?: false)

    engine.toggleVideo()
    assertFalse(engine.activeSession.value?.isVideoEnabled ?: true)

    engine.switchCamera()
    assertFalse(engine.activeSession.value?.isFrontCamera ?: true)
  }
}

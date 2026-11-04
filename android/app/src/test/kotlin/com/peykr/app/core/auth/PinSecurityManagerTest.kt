package com.peykr.app.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinSecurityManagerTest {

  private lateinit var storage: InMemoryPinStorage
  private var currentTime: Long = 1_000_000L
  private lateinit var pinManager: PinSecurityManager

  @Before
  fun setUp() {
    storage = InMemoryPinStorage()
    currentTime = 1_000_000L
    pinManager = PinSecurityManager(
      storage = storage,
      timeProvider = { currentTime },
    )
  }

  @Test
  fun initialSetup_isNotConfigured() {
    assertFalse(pinManager.isPinConfigured())
  }

  @Test
  fun validPinSetup_succeedsAndSetsConfigured() {
    val result = pinManager.setupPin("1234")
    // Note: "1234" is valid non-repeating digits
    assertTrue(result.isSuccess)
    assertTrue(pinManager.isPinConfigured())
  }

  @Test
  fun invalidPin_repeatingDigits_failsSetup() {
    val result = pinManager.setupPin("1111")
    assertTrue(result.isFailure)
    assertFalse(pinManager.isPinConfigured())
  }

  @Test
  fun invalidPin_tooShortOrTooLong_failsSetup() {
    assertTrue(pinManager.setupPin("12").isFailure)
    assertTrue(pinManager.setupPin("123456789").isFailure)
    assertTrue(pinManager.setupPin("abcd").isFailure)
  }

  @Test
  fun correctPinVerification_succeeds() {
    pinManager.setupPin("2468").getOrThrow()
    val verification = pinManager.verifyPin("2468")
    assertEquals(PinVerificationResult.Success, verification)
  }

  @Test
  fun incorrectPinVerification_decrementsAttempts() {
    pinManager.setupPin("2468").getOrThrow()
    val v1 = pinManager.verifyPin("9999")
    assertTrue(v1 is PinVerificationResult.Incorrect)
    assertEquals(4, (v1 as PinVerificationResult.Incorrect).attemptsRemaining)

    val v2 = pinManager.verifyPin("9999")
    assertEquals(3, (v2 as PinVerificationResult.Incorrect).attemptsRemaining)
  }

  @Test
  fun throttling_locksAfter5ConsecutiveFailures() {
    pinManager.setupPin("2468").getOrThrow()
    repeat(4) {
      val v = pinManager.verifyPin("9999")
      assertTrue(v is PinVerificationResult.Incorrect)
    }

    val locked = pinManager.verifyPin("9999")
    assertTrue(locked is PinVerificationResult.LockedOut)
    assertEquals(30L, (locked as PinVerificationResult.LockedOut).lockoutSecondsRemaining)

    // Even correct PIN is rejected during lockout window
    val duringLockout = pinManager.verifyPin("2468")
    assertTrue(duringLockout is PinVerificationResult.LockedOut)

    // Advance time beyond 30s
    currentTime += 31_000L
    val afterLockout = pinManager.verifyPin("2468")
    assertEquals(PinVerificationResult.Success, afterLockout)
  }

  @Test
  fun changePin_withValidCurrentPin_updatesSuccessfully() {
    pinManager.setupPin("1357").getOrThrow()
    val changeResult = pinManager.changePin("1357", "9876")
    assertTrue(changeResult.isSuccess)

    // Old PIN fails
    assertTrue(pinManager.verifyPin("1357") is PinVerificationResult.Incorrect)
    // New PIN succeeds
    assertEquals(PinVerificationResult.Success, pinManager.verifyPin("9876"))
  }

  @Test
  fun changePin_withInvalidCurrentPin_fails() {
    pinManager.setupPin("1357").getOrThrow()
    val changeResult = pinManager.changePin("0000", "9876")
    assertTrue(changeResult.isFailure)
  }
}

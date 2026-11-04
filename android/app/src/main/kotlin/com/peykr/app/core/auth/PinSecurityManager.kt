package com.peykr.app.core.auth

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.pow

sealed interface PinVerificationResult {
  data object Success : PinVerificationResult
  data class Incorrect(val attemptsRemaining: Int) : PinVerificationResult
  data class LockedOut(val lockoutSecondsRemaining: Long) : PinVerificationResult
}

interface PinStorage {
  fun getPinHash(): ByteArray?
  fun getPinSalt(): ByteArray?
  fun savePin(hash: ByteArray, salt: ByteArray)
  fun getFailedAttempts(): Int
  fun setFailedAttempts(count: Int)
  fun getLockoutExpiryMillis(): Long
  fun setLockoutExpiryMillis(expiry: Long)
  fun clear()
}

class InMemoryPinStorage : PinStorage {
  private var hash: ByteArray? = null
  private var salt: ByteArray? = null
  private var failedAttempts: Int = 0
  private var lockoutExpiry: Long = 0L

  override fun getPinHash(): ByteArray? = hash?.clone()
  override fun getPinSalt(): ByteArray? = salt?.clone()
  override fun savePin(hash: ByteArray, salt: ByteArray) {
    this.hash = hash.clone()
    this.salt = salt.clone()
    this.failedAttempts = 0
    this.lockoutExpiry = 0L
  }

  override fun getFailedAttempts(): Int = failedAttempts
  override fun setFailedAttempts(count: Int) {
    this.failedAttempts = count
  }

  override fun getLockoutExpiryMillis(): Long = lockoutExpiry
  override fun setLockoutExpiryMillis(expiry: Long) {
    this.lockoutExpiry = expiry
  }

  override fun clear() {
    hash = null
    salt = null
    failedAttempts = 0
    lockoutExpiry = 0L
  }
}

class PinSecurityManager(
  private val storage: PinStorage,
  private val secureRandom: SecureRandom = SecureRandom(),
  private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

  fun isPinConfigured(): Boolean {
    return storage.getPinHash() != null && storage.getPinSalt() != null
  }

  fun setupPin(pin: String): Result<Unit> = runCatching {
    validatePinComplexity(pin)
    val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
    val hash = deriveKey(pin.toCharArray(), salt)
    storage.savePin(hash, salt)
  }

  fun verifyPin(pin: String): PinVerificationResult {
    val now = timeProvider()
    val lockoutExpiry = storage.getLockoutExpiryMillis()
    if (lockoutExpiry > now) {
      val remainingSecs = ((lockoutExpiry - now) + 999) / 1000
      return PinVerificationResult.LockedOut(remainingSecs)
    }

    val storedHash = storage.getPinHash()
    val storedSalt = storage.getPinSalt()

    if (storedHash == null || storedSalt == null) {
      return PinVerificationResult.Incorrect(0)
    }

    val computedHash = deriveKey(pin.toCharArray(), storedSalt)
    val isMatch = constantTimeEquals(storedHash, computedHash)

    return if (isMatch) {
      storage.setFailedAttempts(0)
      storage.setLockoutExpiryMillis(0L)
      PinVerificationResult.Success
    } else {
      val failed = storage.getFailedAttempts() + 1
      storage.setFailedAttempts(failed)
      if (failed >= MAX_CONSECUTIVE_ATTEMPTS) {
        val extra = failed - MAX_CONSECUTIVE_ATTEMPTS
        val backoffSeconds = (BASE_LOCKOUT_SECONDS * (2.0.pow(extra.toDouble()))).toLong().coerceAtMost(3600L)
        val expiry = now + (backoffSeconds * 1000L)
        storage.setLockoutExpiryMillis(expiry)
        PinVerificationResult.LockedOut(backoffSeconds)
      } else {
        PinVerificationResult.Incorrect(MAX_CONSECUTIVE_ATTEMPTS - failed)
      }
    }
  }

  fun changePin(currentPin: String, newPin: String): Result<Unit> = runCatching {
    when (val verification = verifyPin(currentPin)) {
      is PinVerificationResult.Success -> {
        setupPin(newPin).getOrThrow()
      }
      is PinVerificationResult.LockedOut -> {
        throw IllegalStateException("PIN entry locked for ${verification.lockoutSecondsRemaining} seconds")
      }
      is PinVerificationResult.Incorrect -> {
        throw IllegalArgumentException("Current PIN is incorrect (${verification.attemptsRemaining} attempts left)")
      }
    }
  }

  private fun validatePinComplexity(pin: String) {
    require(pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
      "PIN length must be between $MIN_PIN_LENGTH and $MAX_PIN_LENGTH digits"
    }
    require(pin.all { it.isDigit() }) {
      "PIN must contain digits only"
    }
    // Reject trivial sequences like 1234 or 0000
    val allSame = pin.all { it == pin[0] }
    require(!allSame) { "PIN cannot consist of repeating identical digits" }
  }

  private fun deriveKey(pinChars: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
    val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
    return factory.generateSecret(spec).encoded
  }

  private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) {
      result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
  }

  companion object {
    const val MIN_PIN_LENGTH = 4
    const val MAX_PIN_LENGTH = 8
    const val SALT_BYTES = 32
    const val PBKDF2_ITERATIONS = 100_000
    const val KEY_LENGTH_BITS = 256
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val MAX_CONSECUTIVE_ATTEMPTS = 5
    const val BASE_LOCKOUT_SECONDS = 30L
  }
}

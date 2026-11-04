package com.peykr.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

class SharedPreferencesPinStorage(
  context: Context,
  prefsName: String = PREFS_NAME,
) : PinStorage {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  override fun getPinHash(): ByteArray? {
    val b64 = prefs.getString(KEY_PIN_HASH, null) ?: return null
    return runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrNull()
  }

  override fun getPinSalt(): ByteArray? {
    val b64 = prefs.getString(KEY_PIN_SALT, null) ?: return null
    return runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrNull()
  }

  override fun savePin(hash: ByteArray, salt: ByteArray) {
    val hashB64 = Base64.encodeToString(hash, Base64.NO_WRAP)
    val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
    prefs.edit()
      .putString(KEY_PIN_HASH, hashB64)
      .putString(KEY_PIN_SALT, saltB64)
      .putInt(KEY_FAILED_ATTEMPTS, 0)
      .putLong(KEY_LOCKOUT_EXPIRY, 0L)
      .apply()
  }

  override fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

  override fun setFailedAttempts(count: Int) {
    prefs.edit().putInt(KEY_FAILED_ATTEMPTS, count).apply()
  }

  override fun getLockoutExpiryMillis(): Long = prefs.getLong(KEY_LOCKOUT_EXPIRY, 0L)

  override fun setLockoutExpiryMillis(expiry: Long) {
    prefs.edit().putLong(KEY_LOCKOUT_EXPIRY, expiry).apply()
  }

  override fun clear() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val PREFS_NAME = "peykr_pin_security"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val KEY_LOCKOUT_EXPIRY = "lockout_expiry"
  }
}

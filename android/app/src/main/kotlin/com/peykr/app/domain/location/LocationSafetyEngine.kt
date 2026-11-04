package com.peykr.app.domain.location

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.getOrNull
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.pairing.StrictBase64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LocationSafetyEngine(
  private val deviceIdentity: DeviceIdentity,
  private val contactRepository: TrustedContactRepository,
  private val timeProvider: () -> Long = { System.currentTimeMillis() },
) {

  private val _activeShares = MutableStateFlow<Map<String, LocationShareSession>>(emptyMap())
  val activeShares: StateFlow<Map<String, LocationShareSession>> = _activeShares.asStateFlow()

  private val _safetyConfig = MutableStateFlow(MissedCallSafetyConfig())
  val safetyConfig: StateFlow<MissedCallSafetyConfig> = _safetyConfig.asStateFlow()

  // Track missed calls timestamps per contactId
  private val missedCallHistory = ConcurrentHashMap<String, MutableList<Long>>()

  fun updateSafetyConfig(config: MissedCallSafetyConfig) {
    _safetyConfig.value = config
  }

  suspend fun startSharingLocation(
    recipientContactId: String,
    mode: LocationShareMode,
    durationSeconds: Long = 3600L,
    initialLocation: LocationCoordinates? = null,
  ): Result<LocationShareSession> = runCatching {
    val contact = contactRepository.getById(recipientContactId)
      ?: error("Recipient contact $recipientContactId does not exist")

    require(contact.pairingStatus == PairingStatus.PAIRED) {
      "Cannot share location with contact that is not cryptographically paired"
    }

    val now = timeProvider()
    val expiry = when (mode) {
      LocationShareMode.ONE_TIME -> now + 60_000L
      LocationShareMode.TIME_LIMITED,
      LocationShareMode.LIVE -> now + (durationSeconds * 1000L)
    }

    val sessionId = UUID.randomUUID().toString()
    val session = LocationShareSession(
      sessionId = sessionId,
      recipientContactId = recipientContactId,
      recipientDisplayName = contact.displayName,
      mode = mode,
      startedAtEpochMillis = now,
      expiresAtEpochMillis = expiry,
      lastKnownCoordinates = initialLocation,
      isActive = true,
    )

    val current = _activeShares.value.toMutableMap()
    current[sessionId] = session
    _activeShares.value = current
    session
  }

  fun stopSharing(sessionId: String) {
    val current = _activeShares.value.toMutableMap()
    val existing = current[sessionId] ?: return
    current[sessionId] = existing.copy(isActive = false)
    _activeShares.value = current
  }

  suspend fun createSignedLocationPayload(
    coordinates: LocationCoordinates,
    validDurationSeconds: Long = 60L,
  ): Result<SignedLocationPayload> = runCatching {
    val identity = deviceIdentity.getOrCreate().getOrNull()
      ?: error("Device identity not ready")

    val now = timeProvider()
    require(!coordinates.isStale(now)) { "Cannot transmit stale location data as current location" }

    val canonicalString = "${coordinates.latitude},${coordinates.longitude},${coordinates.accuracyMeters},${coordinates.timestampEpochMillis}"
    val signatureBytes = deviceIdentity.sign(canonicalString.toByteArray()).getOrThrow()
    val sigBase64 = StrictBase64.encode(signatureBytes)

    SignedLocationPayload(
      coordinates = coordinates,
      senderDeviceId = identity.id,
      signatureBase64 = sigBase64,
      expiresAtEpochMillis = now + (validDurationSeconds * 1000L),
    )
  }

  suspend fun recordMissedCall(callerContactId: String, currentLocation: LocationCoordinates?): Boolean {
    val config = _safetyConfig.value
    if (!config.isEnabled) return false
    if (!config.trustedRecipientContactIds.contains(callerContactId)) return false

    val now = timeProvider()
    val list = missedCallHistory.computeIfAbsent(callerContactId) { mutableListOf() }
    val shouldTrigger = synchronized(list) {
      list.add(now)
      val windowMillis = config.timeWindowSeconds * 1000L
      list.removeAll { (now - it) > windowMillis }

      if (list.size >= config.requiredMissedCalls) {
        list.clear()
        true
      } else {
        false
      }
    }

    if (shouldTrigger) {
      for (recipient in config.trustedRecipientContactIds) {
        kotlin.runCatching {
          startSharingLocation(
            recipientContactId = recipient,
            mode = LocationShareMode.TIME_LIMITED,
            durationSeconds = config.autoSharingDurationSeconds,
            initialLocation = currentLocation,
          )
        }
      }
      return true
    }
    return false
  }
}

package com.peykr.app.domain.location

enum class LocationShareMode {
  ONE_TIME,
  TIME_LIMITED,
  LIVE
}

data class LocationCoordinates(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val timestampEpochMillis: Long = System.currentTimeMillis(),
  val altitudeMeters: Double? = null,
  val speedMps: Float? = null,
) {
  fun isStale(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = 120_000L): Boolean =
    (nowMillis - timestampEpochMillis) > maxAgeMillis
}

data class SignedLocationPayload(
  val coordinates: LocationCoordinates,
  val senderDeviceId: String,
  val signatureBase64: String,
  val expiresAtEpochMillis: Long,
)

data class LocationShareSession(
  val sessionId: String,
  val recipientContactId: String,
  val recipientDisplayName: String,
  val mode: LocationShareMode,
  val startedAtEpochMillis: Long = System.currentTimeMillis(),
  val expiresAtEpochMillis: Long,
  val lastKnownCoordinates: LocationCoordinates? = null,
  val isActive: Boolean = true,
)

data class MissedCallSafetyConfig(
  val isEnabled: Boolean = false,
  val requiredMissedCalls: Int = 3,
  val timeWindowSeconds: Long = 300L, // 5 minutes
  val trustedRecipientContactIds: List<String> = emptyList(),
  val autoSharingDurationSeconds: Long = 3600L, // 1 hour
  val cancellationGraceSeconds: Long = 30L,
)

package com.peykr.app.core.identity

data class DeviceIdentityInfo(val id: String, val publicKey: String)

interface DeviceIdentity {
  fun getOrCreate(): DeviceIdentityInfo
}

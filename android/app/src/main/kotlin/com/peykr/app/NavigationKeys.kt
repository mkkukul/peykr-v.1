package com.peykr.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object TrustedPeople : NavKey

@Serializable data object Settings : NavKey

@Serializable data object PairingFlow : NavKey

@Serializable data object DeviceIdentityDetail : NavKey

@Serializable data object BiometricSettingsDetail : NavKey

@Serializable data object LocationSharingDetail : NavKey

@Serializable data object DataBackupDetail : NavKey

@Serializable data object AboutDetail : NavKey

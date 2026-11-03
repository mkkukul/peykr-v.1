package com.peykr.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.peykr.app.core.auth.AndroidBiometricAuthenticator
import com.peykr.app.core.auth.BiometricAuthenticator
import com.peykr.app.core.auth.DefaultSecurityGate
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.identity.AndroidKeyStoreDeviceIdentity
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.pairing.DefaultPairingEngine
import com.peykr.app.domain.pairing.PairingEngine
import java.lang.ref.WeakReference

interface AppContainer {
  val contactRepository: TrustedContactRepository
  val deviceIdentity: DeviceIdentity
  val biometricAuthenticator: BiometricAuthenticator
  val securityGate: SecurityGate
  val pairingEngine: PairingEngine
}

class PeykrApplication : Application(), AppContainer {
  private var activeActivityRef = WeakReference<FragmentActivity>(null)

  override val contactRepository: TrustedContactRepository by lazy {
    SqliteTrustedContactRepository(this)
  }

  override val deviceIdentity: DeviceIdentity by lazy {
    AndroidKeyStoreDeviceIdentity()
  }

  override val biometricAuthenticator: BiometricAuthenticator by lazy {
    AndroidBiometricAuthenticator(this) { activeActivityRef.get() }
  }

  override val securityGate: SecurityGate by lazy {
    DefaultSecurityGate(biometricAuthenticator)
  }

  override val pairingEngine: PairingEngine by lazy {
    DefaultPairingEngine(
      deviceIdentity = deviceIdentity,
      securityGate = securityGate,
      contactRepository = contactRepository,
    )
  }

  override fun onCreate() {
    super.onCreate()
    registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
      override fun onActivityResumed(activity: Activity) {
        if (activity is FragmentActivity) {
          activeActivityRef = WeakReference(activity)
        }
      }

      override fun onActivityPaused(activity: Activity) {
        if (activeActivityRef.get() === activity) {
          activeActivityRef.clear()
        }
      }

      override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
      override fun onActivityStarted(activity: Activity) {}
      override fun onActivityStopped(activity: Activity) {}
      override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
      override fun onActivityDestroyed(activity: Activity) {
        if (activeActivityRef.get() === activity) {
          activeActivityRef.clear()
        }
      }
    })

    deviceIdentity.initialize()
  }

  override fun onTerminate() {
    super.onTerminate()
    deviceIdentity.close()
    (contactRepository as? SqliteTrustedContactRepository)?.close()
  }
}

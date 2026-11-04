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
import com.peykr.app.core.auth.PinSecurityManager
import com.peykr.app.core.auth.SharedPreferencesPinStorage
import com.peykr.app.data.messaging.SqliteMessagingRepository
import com.peykr.app.domain.calling.CallingEngine
import com.peykr.app.domain.calling.DefaultCallingEngine
import com.peykr.app.domain.location.LocationSafetyEngine
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.domain.pairing.DefaultPairingEngine
import com.peykr.app.domain.pairing.PairingEngine
import com.peykr.app.domain.transfer.FileTransferEngine
import java.io.File
import java.lang.ref.WeakReference

interface AppContainer {
  val contactRepository: TrustedContactRepository
  val deviceIdentity: DeviceIdentity
  val biometricAuthenticator: BiometricAuthenticator
  val pinSecurityManager: PinSecurityManager
  val securityGate: SecurityGate
  val pairingEngine: PairingEngine
  val messagingRepository: MessagingRepository
  val messagingEngine: MessagingEngine
  val callingEngine: CallingEngine
  val fileTransferEngine: FileTransferEngine
  val locationSafetyEngine: LocationSafetyEngine
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

  override val pinSecurityManager: PinSecurityManager by lazy {
    PinSecurityManager(SharedPreferencesPinStorage(this))
  }

  override val securityGate: SecurityGate by lazy {
    DefaultSecurityGate(biometricAuthenticator, pinSecurityManager)
  }

  override val pairingEngine: PairingEngine by lazy {
    DefaultPairingEngine(
      deviceIdentity = deviceIdentity,
      securityGate = securityGate,
      contactRepository = contactRepository,
    )
  }

  override val messagingRepository: MessagingRepository by lazy {
    SqliteMessagingRepository(this)
  }

  override val messagingEngine: MessagingEngine by lazy {
    MessagingEngine(
      deviceIdentity = deviceIdentity,
      messagingRepository = messagingRepository,
    )
  }

  override val callingEngine: CallingEngine by lazy {
    DefaultCallingEngine()
  }

  override val fileTransferEngine: FileTransferEngine by lazy {
    FileTransferEngine(
      transferCacheDir = File(cacheDir, "transfers"),
      completedDownloadsDir = File(getExternalFilesDir(null) ?: filesDir, "PeykrDownloads"),
    )
  }

  override val locationSafetyEngine: LocationSafetyEngine by lazy {
    LocationSafetyEngine(
      deviceIdentity = deviceIdentity,
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
    (messagingRepository as? SqliteMessagingRepository)?.close()
  }
}

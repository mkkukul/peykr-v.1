package com.peykr.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.peykr.app.core.auth.AndroidBiometricAuthenticator
import com.peykr.app.core.auth.BiometricAuthenticator
import com.peykr.app.core.auth.DefaultSecurityGate
import com.peykr.app.core.auth.PinSecurityManager
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.auth.SharedPreferencesPinStorage
import com.peykr.app.core.identity.AndroidKeyStoreDeviceIdentity
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.getOrNull
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.data.messaging.SqliteMessagingRepository
import com.peykr.app.domain.calling.CallingEngine
import com.peykr.app.domain.calling.DefaultCallingEngine
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.domain.localsend.LocalHttpClient
import com.peykr.app.domain.localsend.LocalHttpServer
import com.peykr.app.domain.localsend.LocalNetworkDiscovery
import com.peykr.app.domain.location.LocationSafetyEngine
import com.peykr.app.domain.mesh.BleMeshEngine
import com.peykr.app.domain.mesh.BleMeshTransport
import com.peykr.app.domain.messaging.MessageStatus
import com.peykr.app.domain.messaging.MessageTransportType
import com.peykr.app.domain.messaging.MessagingEngine
import com.peykr.app.domain.messaging.MessagingRepository
import com.peykr.app.domain.pairing.DefaultPairingEngine
import com.peykr.app.domain.pairing.PairingEngine
import com.peykr.app.domain.transfer.FileTransferEngine
import com.peykr.app.domain.transport.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
  val bleMeshEngine: BleMeshEngine
  val bleMeshTransport: BleMeshTransport
  val localNetworkDiscovery: LocalNetworkDiscovery
  val localHttpServer: LocalHttpServer
  val localHttpClient: LocalHttpClient
  val transportManager: TransportManager
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
      ratchetStore = com.peykr.app.core.security.FileRatchetStore(File(filesDir, "security/ratchets")),
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

  override val bleMeshEngine: BleMeshEngine by lazy {
    val id = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    BleMeshEngine(localDeviceId = id)
  }

  override val bleMeshTransport: BleMeshTransport by lazy {
    BleMeshTransport(this, bleMeshEngine)
  }

  override val localHttpServer: LocalHttpServer by lazy {
    val id = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val fp = deviceIdentity.getOrCreate().getOrNull()?.publicKey ?: ""
    LocalHttpServer(
      localDeviceId = id,
      localDisplayName = "Peykr Cihazı",
      localFingerprint = fp,
      fileTransferEngine = fileTransferEngine,
      messagingEngine = messagingEngine,
      contactRepository = contactRepository,
      deviceIdentity = deviceIdentity,
      localNetworkDiscovery = localNetworkDiscovery,
      messagingRepository = messagingRepository,
      onDirectMessageReceived = { msg ->
        CoroutineScope(Dispatchers.IO).launch {
          val senderId = msg.senderId
          val convId = "conv_$senderId"
          val contact = contactRepository.getById(senderId)
          val title = contact?.displayName ?: "Peykr Kullanıcısı"

          messagingRepository.saveConversation(
            com.peykr.app.domain.messaging.Conversation(
              id = convId,
              title = title,
              isGroup = false,
              createdAt = System.currentTimeMillis(),
              updatedAt = msg.timestamp,
              lastMessage = msg.textContent ?: "Mesaj alındı",
              unreadCount = 1,
            )
          )

          messagingRepository.saveMessage(
            msg.copy(
              conversationId = convId,
              direction = com.peykr.app.domain.messaging.MessageDirection.INCOMING,
              status = MessageStatus.DELIVERED,
              transportType = MessageTransportType.LOCAL_WIFI,
              verifiedDeliveryEpochMillis = System.currentTimeMillis(),
              isRead = false,
            )
          )
        }
      }
    )
  }

  override val localNetworkDiscovery: LocalNetworkDiscovery by lazy {
    val id = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    val fp = deviceIdentity.getOrCreate().getOrNull()?.publicKey ?: ""
    LocalNetworkDiscovery(
      context = this,
      localDeviceId = id,
      localDisplayName = "Peykr Cihazı",
      localPort = LocalHttpServer.DEFAULT_PORT,
      localFingerprint = fp,
    )
  }

  override val localHttpClient: LocalHttpClient by lazy {
    val id = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    LocalHttpClient(localDeviceId = id, localDisplayName = "Peykr Cihazı")
  }

  override val transportManager: TransportManager by lazy {
    val id = deviceIdentity.getOrCreate().getOrNull()?.id ?: "local"
    TransportManager(
      localDeviceId = id,
      localDisplayName = "Peykr Cihazı",
      messagingRepository = messagingRepository,
      messagingEngine = messagingEngine,
      bleMeshEngine = bleMeshEngine,
      localNetworkDiscovery = localNetworkDiscovery,
      localHttpClient = localHttpClient,
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

    // Start local background mesh & Wi-Fi transports safely
    try {
      localHttpServer.start()
      localNetworkDiscovery.start()
      bleMeshTransport.start()
    } catch (e: Exception) {
      android.util.Log.w("PeykrApp", "Transports initialized with fallbacks: ${e.message}")
    }
  }

  override fun onTerminate() {
    super.onTerminate()
    deviceIdentity.close()
    localNetworkDiscovery.stop()
    localHttpServer.stop()
    bleMeshTransport.stop()
    (contactRepository as? SqliteTrustedContactRepository)?.close()
    (messagingRepository as? SqliteMessagingRepository)?.close()
  }
}

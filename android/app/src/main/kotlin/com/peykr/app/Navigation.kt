package com.peykr.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.peykr.app.core.auth.SecurityGate
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.data.AppThemeMode
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.calling.ActiveCallScreen
import com.peykr.app.ui.main.MainScreen
import com.peykr.app.ui.main.SettingsScreen
import com.peykr.app.ui.main.TrustedPeopleScreen
import com.peykr.app.ui.messaging.ConversationDetailScreen
import com.peykr.app.ui.messaging.MessagesListScreen
import com.peykr.app.ui.pairing.PairingScreen
import com.peykr.app.ui.settings.AboutDetailScreen
import com.peykr.app.ui.settings.BiometricSettingsDetailScreen
import com.peykr.app.ui.settings.DataBackupDetailScreen
import com.peykr.app.ui.settings.DeviceIdentityDetailScreen
import com.peykr.app.ui.settings.LocationSharingDetailScreen
import com.peykr.app.ui.transfer.FileTransferScreen
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
  language: AppLanguage,
  onLanguageSelected: (AppLanguage) -> Unit,
  themeMode: AppThemeMode = AppThemeMode.SYSTEM,
  onThemeModeSelected: (AppThemeMode) -> Unit = {},
  contactRepository: TrustedContactRepository? = null,
  deviceIdentity: DeviceIdentity? = null,
  securityGate: SecurityGate? = null,
) {
  val context = LocalContext.current
  val repository = contactRepository
    ?: (context.applicationContext as? AppContainer)?.contactRepository
    ?: remember(context.applicationContext) { SqliteTrustedContactRepository(context.applicationContext) }
  val identity = deviceIdentity
    ?: (context.applicationContext as? AppContainer)?.deviceIdentity
  val gate = securityGate
    ?: (context.applicationContext as? AppContainer)?.securityGate
  val backStack = rememberNavBackStack(Main)
  fun navigateTo(key: NavKey) {
    if (backStack.lastOrNull() == key) return
    val topLevelTabs = setOf(Main, MessagesList, TrustedPeople, Settings)
    if (key in topLevelTabs) {
      while (backStack.size > 1) {
        backStack.removeLastOrNull()
      }
      if (key != Main) {
        backStack.add(key)
      }
    } else {
      backStack.add(key)
    }
  }

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          MainScreen(
            onNavigate = ::navigateTo,
            onPeopleClick = { navigateTo(TrustedPeople) },
            onSettingsClick = { navigateTo(Settings) },
            contactRepository = repository,
            deviceIdentity = identity,
            securityGate = gate,
          )
        }
        entry<TrustedPeople> {
          TrustedPeopleScreen(
            onNavigate = ::navigateTo,
            onBack = { backStack.removeLastOrNull() },
            contactRepository = repository,
            deviceIdentity = identity,
          )
        }
        entry<Settings> {
          SettingsScreen(
            onNavigate = ::navigateTo,
            onBack = { backStack.removeLastOrNull() },
            selectedLanguage = language,
            onLanguageSelected = onLanguageSelected,
            selectedThemeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            deviceIdentity = identity,
            securityGate = gate,
          )
        }
        entry<DeviceIdentityDetail> {
          DeviceIdentityDetailScreen(
            deviceIdentity = identity,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<BiometricSettingsDetail> {
          val appContainer = context.applicationContext as? AppContainer
          val pinManager = appContainer?.pinSecurityManager
          BiometricSettingsDetailScreen(
            securityGate = gate,
            pinSecurityManager = pinManager,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<LocationSharingDetail> {
          LocationSharingDetailScreen(
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<DataBackupDetail> {
          DataBackupDetailScreen(
            contactRepository = repository,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<AboutDetail> {
          AboutDetailScreen(
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<PairingFlow> {
          val appContainer = context.applicationContext as? AppContainer
          val engine = appContainer?.pairingEngine
            ?: remember(identity, gate, repository) {
              com.peykr.app.domain.pairing.DefaultPairingEngine(
                deviceIdentity = identity ?: error("DeviceIdentity required"),
                securityGate = gate ?: error("SecurityGate required"),
                contactRepository = repository,
              )
            }
          PairingScreen(
            onBack = { backStack.removeLastOrNull() },
            pairingEngine = engine,
            securityGate = gate ?: error("SecurityGate required"),
            deviceIdentity = identity,
            localHttpClient = appContainer?.localHttpClient,
            contactRepository = repository,
            localHttpServer = appContainer?.localHttpServer,
            localNetworkDiscovery = appContainer?.localNetworkDiscovery,
          )
        }
        entry<MessagesList> {
          val appContainer = context.applicationContext as? AppContainer
          val msgRepo = appContainer?.messagingRepository ?: error("MessagingRepository required")
          val msgEngine = appContainer.messagingEngine
          MessagesListScreen(
            messagingRepository = msgRepo,
            contactRepository = repository,
            messagingEngine = msgEngine,
            onOpenConversation = { id, title -> navigateTo(ConversationDetail(id, title)) },
            onBack = { backStack.removeLastOrNull() },
            onNavigate = ::navigateTo,
          )
        }
        entry<ConversationDetail> { key ->
          val appContainer = context.applicationContext as? AppContainer
          val msgRepo = appContainer?.messagingRepository ?: error("MessagingRepository required")
          val msgEngine = appContainer.messagingEngine
          val transportMgr = appContainer.transportManager
          ConversationDetailScreen(
            conversationId = key.conversationId,
            title = key.title,
            messagingRepository = msgRepo,
            messagingEngine = msgEngine,
            transportManager = transportMgr,
            onStartCall = { contactId, name ->
              val callingEngine = appContainer.callingEngine
              kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                callingEngine.startCall(contactId, name, com.peykr.app.domain.calling.CallType.VOICE)
                navigateTo(ActiveCall)
              }
            },
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<ActiveCall> {
          val appContainer = context.applicationContext as? AppContainer
          val callEngine = appContainer?.callingEngine ?: error("CallingEngine required")
          ActiveCallScreen(
            callingEngine = callEngine,
            onBack = { backStack.removeLastOrNull() },
          )
        }
        entry<FileTransfer> {
          val appContainer = context.applicationContext as? AppContainer
          val transferEngine = appContainer?.fileTransferEngine ?: error("FileTransferEngine required")
          FileTransferScreen(
            transferEngine = transferEngine,
            discovery = appContainer.localNetworkDiscovery,
            httpClient = appContainer.localHttpClient,
            onBack = { backStack.removeLastOrNull() },
          )
        }
      },
  )
}

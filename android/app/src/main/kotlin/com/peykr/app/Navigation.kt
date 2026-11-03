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
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.localization.AppLanguage
import com.peykr.app.ui.main.MainScreen
import com.peykr.app.ui.main.SettingsScreen
import com.peykr.app.ui.main.TrustedPeopleScreen

@Composable
fun MainNavigation(
  language: AppLanguage,
  onLanguageSelected: (AppLanguage) -> Unit,
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
    while (backStack.size > 1 && backStack.contains(key)) backStack.removeLastOrNull()
    backStack.add(key)
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
            deviceIdentity = identity,
            securityGate = gate,
          )
        }
      },
  )
}

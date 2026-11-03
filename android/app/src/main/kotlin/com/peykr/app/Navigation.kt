package com.peykr.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.platform.LocalContext
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.domain.contacts.TrustedContactRepository
import com.peykr.app.ui.main.MainScreen
import com.peykr.app.ui.main.SettingsScreen
import com.peykr.app.ui.main.TrustedPeopleScreen
import com.peykr.app.localization.AppLanguage

@Composable
fun MainNavigation(
  language: AppLanguage,
  onLanguageSelected: (AppLanguage) -> Unit,
  contactRepository: TrustedContactRepository? = null,
) {
  val context = LocalContext.current
  val repository = contactRepository
    ?: (context.applicationContext as? AppContainer)?.contactRepository
    ?: remember(context.applicationContext) { SqliteTrustedContactRepository(context.applicationContext) }
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
            )
          }
          entry<TrustedPeople> {
            TrustedPeopleScreen(
              onNavigate = ::navigateTo,
              onBack = { backStack.removeLastOrNull() },
              contactRepository = repository,
            )
          }
          entry<Settings> {
            SettingsScreen(
              onNavigate = ::navigateTo,
              onBack = { backStack.removeLastOrNull() },
              selectedLanguage = language,
              onLanguageSelected = onLanguageSelected,
            )
          }
        },
  )
}

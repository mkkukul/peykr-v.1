package com.peykr.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.platform.LocalContext
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.ui.main.MainScreen
import com.peykr.app.ui.main.SettingsScreen
import com.peykr.app.ui.main.TrustedPeopleScreen
import com.peykr.app.localization.AppLanguage

@Composable
fun MainNavigation(language: AppLanguage, onLanguageSelected: (AppLanguage) -> Unit) {
  val context = LocalContext.current
  val contactRepository = remember(context) { SqliteTrustedContactRepository(context) }
  DisposableEffect(contactRepository) {
    onDispose { contactRepository.close() }
  }
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
              contactRepository = contactRepository,
            )
          }
          entry<TrustedPeople> {
            TrustedPeopleScreen(
              onNavigate = ::navigateTo,
              onBack = { backStack.removeLastOrNull() },
              contactRepository = contactRepository,
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

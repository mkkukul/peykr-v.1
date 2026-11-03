package com.peykr.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.peykr.app.ui.main.MainScreen
import com.peykr.app.ui.main.SettingsScreen
import com.peykr.app.ui.main.TrustedPeopleScreen

@Composable
fun MainNavigation() {
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
            )
          }
          entry<TrustedPeople> {
            TrustedPeopleScreen(
              onNavigate = ::navigateTo,
              onBack = { backStack.removeLastOrNull() },
            )
          }
          entry<Settings> {
            SettingsScreen(
              onNavigate = ::navigateTo,
              onBack = { backStack.removeLastOrNull() },
            )
          }
        },
  )
}

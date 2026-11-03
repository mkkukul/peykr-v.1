package com.peykr.app

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.peykr.app.ui.main.MainScreen
import com.peykr.app.ui.main.SettingsScreen
import com.peykr.app.ui.main.TrustedPeopleScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
            MainScreen(
              onNavigate = { navKey -> backStack.add(navKey) },
              onPeopleClick = { backStack.add(TrustedPeople) },
              onSettingsClick = { backStack.add(Settings) },
            )
          }
          entry<TrustedPeople> {
            TrustedPeopleScreen(
              onNavigate = { navKey -> backStack.add(navKey) },
              onBack = { backStack.removeLastOrNull() },
            )
          }
          entry<Settings> {
            SettingsScreen(
              onNavigate = { navKey -> backStack.add(navKey) },
              onBack = { backStack.removeLastOrNull() },
            )
          }
        },
  )
}

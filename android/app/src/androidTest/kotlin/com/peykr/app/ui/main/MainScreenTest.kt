package com.peykr.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.peykr.app.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent {
      MainScreen({}, {}, {}, FakeContactRepository())
    }
  }

  @Test
  fun homeScreen_exists() {
    composeTestRule.onNodeWithText("Peykr").assertExists()
  }
}

private class FakeContactRepository : TrustedContactRepository {
  override val contacts: Flow<List<TrustedContact>> = flowOf(emptyList())
  override suspend fun getById(id: String): TrustedContact? = null
  override suspend fun add(contact: TrustedContact) = Unit
  override suspend fun update(contact: TrustedContact) = Unit
  override suspend fun delete(id: String) = Unit
}

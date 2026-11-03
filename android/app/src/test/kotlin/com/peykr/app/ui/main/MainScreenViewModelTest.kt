package com.peykr.app.ui.main

import com.peykr.app.data.contacts.DatabaseState
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeContactRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val viewModel = MainScreenViewModel(FakeContactRepository())
    val state = viewModel.uiState.first { it is MainScreenUiState.Success } as MainScreenUiState.Success
    assertEquals(listOf("Sample"), state.data.map { it.displayName })
  }

  @Test
  fun databaseInitializationFailure_isNotDisplayedAsEmptyContactList() = runTest {
    val failingRepo = FakeContactRepository(
      initialDbState = DatabaseState.KEY_MISSING,
      initialContacts = emptyList()
    )
    val viewModel = MainScreenViewModel(failingRepo)
    val state = viewModel.uiState.first { it !is MainScreenUiState.Loading }

    assertTrue("State must be DatabaseError, but was $state", state is MainScreenUiState.DatabaseError)
    assertEquals(DatabaseState.KEY_MISSING, (state as MainScreenUiState.DatabaseError).state)
  }

  @Test
  fun retry_delegatesToRepository() = runTest {
    val repo = FakeContactRepository()
    val viewModel = MainScreenViewModel(repo)
    viewModel.retry()
    assertTrue(repo.retryCalled)
  }
}

private class FakeContactRepository(
  initialDbState: DatabaseState = DatabaseState.READY,
  initialContacts: List<TrustedContact> = listOf(TrustedContact("sample", "Sample", createdAtEpochMillis = 1L))
) : TrustedContactRepository {
  var retryCalled = false
  private val dbStateFlow = MutableStateFlow(initialDbState)
  private val contactsFlow = MutableStateFlow(initialContacts)

  override val initializationState: Flow<DatabaseState> = dbStateFlow
  override val contacts: Flow<List<TrustedContact>> = contactsFlow

  override fun retry() {
    retryCalled = true
  }

  override suspend fun getById(id: String) = contactsFlow.value.find { it.id == id }
  override suspend fun add(contact: TrustedContact) {
    contactsFlow.value = contactsFlow.value + contact
  }
  override suspend fun update(contact: TrustedContact) {
    contactsFlow.value = contactsFlow.value.map { if (it.id == contact.id) contact else it }
  }
  override suspend fun delete(id: String) {
    contactsFlow.value = contactsFlow.value.filterNot { it.id == id }
  }
}

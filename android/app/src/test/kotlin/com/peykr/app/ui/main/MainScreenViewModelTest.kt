package com.peykr.app.ui.main

import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
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
}

private class FakeContactRepository : TrustedContactRepository {
  private val sample = TrustedContact("sample", "Sample", createdAtEpochMillis = 1L)
  override val contacts: Flow<List<TrustedContact>> = flow { emit(listOf(sample)) }
  override suspend fun getById(id: String) = if (id == sample.id) sample else null
  override suspend fun add(contact: TrustedContact) = Unit
  override suspend fun update(contact: TrustedContact) = Unit
  override suspend fun delete(id: String) = Unit
}

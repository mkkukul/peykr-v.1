package com.peykr.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peykr.app.data.contacts.DatabaseState
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainScreenViewModel(private val dataRepository: TrustedContactRepository) : ViewModel() {
  val uiState: StateFlow<MainScreenUiState> =
    combine(dataRepository.initializationState, dataRepository.contacts) { dbState, contacts ->
      when (dbState) {
        DatabaseState.INITIALIZING, DatabaseState.MIGRATION_IN_PROGRESS -> MainScreenUiState.Loading
        DatabaseState.READY -> MainScreenUiState.Success(contacts)
        DatabaseState.KEY_MISSING,
        DatabaseState.KEY_INVALID,
        DatabaseState.CORRUPTED,
        DatabaseState.MIGRATION_FAILED,
        DatabaseState.MIGRATION_REQUIRED -> MainScreenUiState.DatabaseError(dbState)
      }
    }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun retry() {
    dataRepository.retry()
  }
}

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState

  data class DatabaseError(val state: DatabaseState) : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(val data: List<TrustedContact>) : MainScreenUiState
}

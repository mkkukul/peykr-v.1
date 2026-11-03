package com.peykr.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.data.contacts.DatabaseState
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
  data object Loading : MainScreenUiState

  data class DatabaseError(
    val state: DatabaseState,
    val diagnosticMessage: String? = null,
  ) : MainScreenUiState

  data class IdentityError(
    val state: DeviceIdentityState,
    val isRecoverable: Boolean = state is DeviceIdentityState.KeystoreUnavailable,
  ) : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(
    val data: List<TrustedContact>,
    val identityState: DeviceIdentityState = DeviceIdentityState.Ready(DeviceIdentityInfo("", "")),
    val dbState: DatabaseState = DatabaseState.READY,
  ) : MainScreenUiState
}

class MainScreenViewModel(
  private val dataRepository: TrustedContactRepository,
  private val deviceIdentity: DeviceIdentity? = null,
) : ViewModel() {

  private val identityFlow: StateFlow<DeviceIdentityState> =
    deviceIdentity?.identityState
      ?: MutableStateFlow(DeviceIdentityState.Ready(DeviceIdentityInfo("local", "")))

  val uiState: StateFlow<MainScreenUiState> =
    combine(
      dataRepository.initializationState,
      dataRepository.contacts,
      identityFlow,
      dataRepository.diagnosticDetails,
    ) { dbState, contacts, idState, diagMsg ->
      when {
        dbState == DatabaseState.INITIALIZING || dbState == DatabaseState.MIGRATION_IN_PROGRESS || idState is DeviceIdentityState.Initializing -> {
          MainScreenUiState.Loading
        }
        dbState in listOf(
          DatabaseState.KEY_MISSING,
          DatabaseState.KEY_INVALID,
          DatabaseState.FILE_ACCESS_ERROR,
          DatabaseState.SCHEMA_ERROR,
          DatabaseState.CORRUPTED,
          DatabaseState.MIGRATION_FAILED,
          DatabaseState.MIGRATION_REQUIRED,
        ) -> {
          MainScreenUiState.DatabaseError(dbState, diagMsg)
        }
        idState is DeviceIdentityState.KeystoreUnavailable ||
        idState is DeviceIdentityState.IdentityInvalidated ||
        idState is DeviceIdentityState.GenerationFailed -> {
          MainScreenUiState.IdentityError(
            state = idState,
            isRecoverable = idState is DeviceIdentityState.KeystoreUnavailable,
          )
        }
        dbState == DatabaseState.READY && idState is DeviceIdentityState.Ready -> {
          MainScreenUiState.Success(data = contacts, identityState = idState, dbState = dbState)
        }
        else -> {
          MainScreenUiState.Loading
        }
      }
    }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun retry() {
    dataRepository.retry()
    deviceIdentity?.retry()
  }

  fun deleteContact(id: String) {
    viewModelScope.launch {
      runCatching { dataRepository.delete(id) }
    }
  }
}

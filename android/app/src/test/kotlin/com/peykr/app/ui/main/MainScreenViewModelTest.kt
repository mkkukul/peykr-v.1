package com.peykr.app.ui.main

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.data.contacts.DatabaseState
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import java.security.PublicKey
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
  fun identityError_emitsIdentityErrorState() = runTest {
    val repo = FakeContactRepository(initialDbState = DatabaseState.READY)
    val fakeIdentity = FakeDeviceIdentity(
      initialState = DeviceIdentityState.KeystoreUnavailable(IllegalStateException("Simulated Keystore lock"))
    )
    val viewModel = MainScreenViewModel(repo, fakeIdentity)
    val state = viewModel.uiState.first { it !is MainScreenUiState.Loading }

    assertTrue("State must be IdentityError, but was $state", state is MainScreenUiState.IdentityError)
    val identityError = state as MainScreenUiState.IdentityError
    assertTrue(identityError.isRecoverable)
  }

  @Test
  fun invalidatedIdentity_isNotRecoverableWithoutAction() = runTest {
    val repo = FakeContactRepository(initialDbState = DatabaseState.READY)
    val fakeIdentity = FakeDeviceIdentity(
      initialState = DeviceIdentityState.IdentityInvalidated("Certificate missing")
    )
    val viewModel = MainScreenViewModel(repo, fakeIdentity)
    val state = viewModel.uiState.first { it !is MainScreenUiState.Loading }

    assertTrue(state is MainScreenUiState.IdentityError)
    val identityError = state as MainScreenUiState.IdentityError
    assertFalse(identityError.isRecoverable)
  }

  @Test
  fun retry_delegatesToBothRepositoryAndIdentity() = runTest {
    val repo = FakeContactRepository()
    val fakeIdentity = FakeDeviceIdentity()
    val viewModel = MainScreenViewModel(repo, fakeIdentity)
    viewModel.retry()

    assertTrue(repo.retryCalled)
    assertTrue(fakeIdentity.retryCalled)
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

private class FakeDeviceIdentity(
  initialState: DeviceIdentityState = DeviceIdentityState.Ready(DeviceIdentityInfo("test-id", "test-pk"))
) : DeviceIdentity {
  var retryCalled = false
  var initializeCalled = false
  val stateFlow = MutableStateFlow(initialState)

  override val identityState: StateFlow<DeviceIdentityState> = stateFlow

  override fun initialize() {
    initializeCalled = true
  }

  override fun retry() {
    retryCalled = true
  }

  override fun getOrCreate(): DeviceIdentityResult =
    when (val s = stateFlow.value) {
      is DeviceIdentityState.Ready -> DeviceIdentityResult.Success(s.identity)
      is DeviceIdentityState.KeystoreUnavailable -> DeviceIdentityResult.KeystoreUnavailable(s.cause)
      is DeviceIdentityState.IdentityInvalidated -> DeviceIdentityResult.IdentityInvalidated(s.message, s.cause)
      is DeviceIdentityState.GenerationFailed -> DeviceIdentityResult.GenerationFailed(s.cause)
      is DeviceIdentityState.Initializing -> DeviceIdentityResult.GenerationFailed(IllegalStateException("Still initializing"))
    }

  override fun sign(transcript: ByteArray): Result<ByteArray> = Result.success(byteArrayOf(1, 2, 3))
  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun verify(publicKey: PublicKey, transcript: ByteArray, signature: ByteArray): Boolean = true
}

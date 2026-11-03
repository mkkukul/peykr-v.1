package com.peykr.app.domain.contacts

import com.peykr.app.data.contacts.DatabaseState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TrustedContactRepositorySafetyTest {

  @Test
  fun contactOperations_cannotRunWhenDatabaseFailsInitialization() = runTest {
    val unreadyRepo = UnreadyTestRepository(DatabaseState.KEY_MISSING)

    val exception = assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking {
        unreadyRepo.add(TrustedContact("test", "Test Contact", createdAtEpochMillis = 100L))
      }
    }

    assertEquals("Database is not ready: KEY_MISSING", exception.message)
  }

  @Test
  fun contactRead_cannotRunWhenDatabaseIsCorrupted() = runTest {
    val unreadyRepo = UnreadyTestRepository(DatabaseState.CORRUPTED)

    val exception = assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking {
        unreadyRepo.getById("test")
      }
    }

    assertEquals("Database is not ready: CORRUPTED", exception.message)
  }
}

private class UnreadyTestRepository(
  private val state: DatabaseState
) : TrustedContactRepository {
  private val stateFlow = MutableStateFlow(state)
  private val contactsFlow = MutableStateFlow<List<TrustedContact>>(emptyList())

  override val initializationState: Flow<DatabaseState> = stateFlow
  override val contacts: Flow<List<TrustedContact>> = contactsFlow

  private fun checkReady() {
    check(state == DatabaseState.READY) { "Database is not ready: $state" }
  }

  override suspend fun getById(id: String): TrustedContact? {
    checkReady()
    return null
  }

  override suspend fun add(contact: TrustedContact) {
    checkReady()
  }

  override suspend fun update(contact: TrustedContact) {
    checkReady()
  }

  override suspend fun delete(id: String) {
    checkReady()
  }
}

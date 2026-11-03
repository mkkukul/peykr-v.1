package com.peykr.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peykr.app.core.identity.AndroidKeyStoreDeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.data.contacts.SqliteTrustedContactRepository
import com.peykr.app.domain.contacts.TrustedContact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Phase1PersistenceTest {
  @Test
  fun deviceIdentity_isStableAcrossReads() {
    val identity = AndroidKeyStoreDeviceIdentity()
    val first = identity.getOrCreate()
    val second = identity.getOrCreate()
    assertTrue(first is DeviceIdentityResult.Success)
    assertEquals(first, second)
  }

  @Test
  fun contact_survivesDatabaseReopen() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.cacheDir, "peykr-test-${System.nanoTime()}.db")
    val contact = TrustedContact("contact-1", "Test contact", createdAtEpochMillis = 42L)
    try {
      val firstRepository = SqliteTrustedContactRepository(context, file.absolutePath)
      firstRepository.add(contact)
      firstRepository.close()
      val reopenedRepository = SqliteTrustedContactRepository(context, file.absolutePath)
      assertEquals(contact, reopenedRepository.contacts.first().single())
      reopenedRepository.close()
    } finally {
      file.delete()
      File("${file.path}-shm").delete()
      File("${file.path}-wal").delete()
    }
  }

  @Test
  fun repository_initializationAndDisposalAreSafe() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val file = File(context.cacheDir, "peykr-safe-${System.nanoTime()}.db")
    try {
      val repo = SqliteTrustedContactRepository(context, file.absolutePath)
      repo.close()
      // Disposing should not crash or throw unhandled exceptions
    } finally {
      file.delete()
    }
  }
}

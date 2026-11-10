package com.peykr.app.data.contacts

import android.content.ContentValues
import android.content.Context
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase

class SqliteTrustedContactRepository(context: Context, databaseName: String = EncryptedContactDatabase.DATABASE_NAME) : TrustedContactRepository {
  private val databaseContext = context.applicationContext
  private val databaseName = databaseName
  private val state = MutableStateFlow<List<TrustedContact>>(emptyList())
  private val initialization = MutableStateFlow(DatabaseState.INITIALIZING)
  private val diagnostic = MutableStateFlow<String?>(null)
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  @Volatile private var database: SQLiteDatabase? = null

  init {
    startInitialization()
  }

  private fun startInitialization() {
    ioScope.launch {
      try {
        android.util.Log.i("PeykrDatabase", "Starting initialization for database: $databaseName")
        diagnostic.value = null
        val opened = EncryptedContactDatabase(databaseContext, databaseName).open()
        if (!isActive) { opened.close(); return@launch }
        synchronized(this@SqliteTrustedContactRepository) {
          database = opened
        }
        state.value = readAll(opened)
        initialization.value = DatabaseState.READY
        android.util.Log.i("PeykrDatabase", "Database initialization successful. Loaded ${state.value.size} contacts")
      } catch (error: Exception) {
        val resolvedState = (error as? DatabaseInitializationException)?.state ?: DatabaseState.KEY_INVALID
        initialization.value = resolvedState
        diagnostic.value = error.message ?: error.javaClass.simpleName
        android.util.Log.e("PeykrDatabase", "Database initialization failed: state=$resolvedState, message=${error.message}", error)
      }
    }
  }

  override val contacts: Flow<List<TrustedContact>> = state
  override val initializationState: StateFlow<DatabaseState> = initialization
  override val diagnosticDetails: StateFlow<String?> = diagnostic

  override fun retry() {
    synchronized(this) {
      if (database != null && initialization.value == DatabaseState.READY) return
      val current = database
      database = null
      current?.close()
      initialization.value = DatabaseState.INITIALIZING
      diagnostic.value = null
    }
    startInitialization()
  }

  private suspend fun ensureReady(): SQLiteDatabase {
    if (initialization.value == DatabaseState.INITIALIZING) {
      initialization.first { it != DatabaseState.INITIALIZING }
    }
    check(initialization.value == DatabaseState.READY) { "Database is not ready: ${initialization.value}" }
    return database ?: error("Database is not ready: ${initialization.value}")
  }

  override suspend fun getById(id: String): TrustedContact? = withContext(Dispatchers.IO) {
    val db = ensureReady()
    db.query(EncryptedContactDatabase.TABLE, EncryptedContactDatabase.COLUMNS, "id = ?", arrayOf(id), null, null, null, "1").use { if (it.moveToFirst()) it.toContact() else null }
  }

  override suspend fun add(contact: TrustedContact) = withContext(Dispatchers.IO) {
    validate(contact)
    ensureReady().insertWithOnConflict(EncryptedContactDatabase.TABLE, null, contact.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    publish()
  }

  override suspend fun update(contact: TrustedContact) = withContext(Dispatchers.IO) {
    validate(contact)
    check(ensureReady().update(EncryptedContactDatabase.TABLE, contact.toValues(), "id = ?", arrayOf(contact.id)) == 1) { "Trusted contact was not found: ${contact.id}" }
    publish()
  }

  override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
    require(id.isNotBlank()) { "Contact id must not be blank" }
    ensureReady().delete(EncryptedContactDatabase.TABLE, "id = ?", arrayOf(id))
    publish()
  }

  fun close() {
    ioScope.cancel()
    synchronized(this) {
      database?.close()
      database = null
    }
  }

  private fun publish() { state.value = readAll(database ?: return) }
  private fun readAll(db: SQLiteDatabase): List<TrustedContact> = db.query(EncryptedContactDatabase.TABLE, EncryptedContactDatabase.COLUMNS, null, null, null, null, "created_at ASC").use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toContact()) } }
  private fun validate(contact: TrustedContact) { require(contact.id.isNotBlank()) { "Contact id must not be blank" }; require(contact.displayName.isNotBlank()) { "Contact display name must not be blank" } }
}

private fun TrustedContact.toValues() = ContentValues().apply {
  put("id", id); put("display_name", displayName); put("public_identity", publicIdentity); put("pairing_status", pairingStatus.name); put("created_at", createdAtEpochMillis); put("revoked_at", revokedAtEpochMillis)
}

private fun android.database.Cursor.toContact() = TrustedContact(
  id = getString(0), displayName = getString(1), publicIdentity = if (isNull(2)) null else getString(2),
  pairingStatus = runCatching {
    val raw = getString(3)
    if (raw == "PENDING") PairingStatus.PAIRING else PairingStatus.valueOf(raw)
  }.getOrDefault(PairingStatus.NOT_PAIRED),
  createdAtEpochMillis = getLong(4), revokedAtEpochMillis = if (isNull(5)) null else getLong(5),
)

package com.peykr.app.data.contacts

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.peykr.app.domain.contacts.PairingStatus
import com.peykr.app.domain.contacts.TrustedContact
import com.peykr.app.domain.contacts.TrustedContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqliteTrustedContactRepository(context: Context, databaseName: String = DEFAULT_DATABASE_NAME) : TrustedContactRepository {
  private val helper = ContactDatabase(context.applicationContext, databaseName)
  private val state = MutableStateFlow<List<TrustedContact>>(emptyList())
  private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  init {
    ioScope.launch { state.value = helper.readAll() }
  }

  override val contacts: Flow<List<TrustedContact>> = state

  override suspend fun getById(id: String): TrustedContact? = withContext(Dispatchers.IO) {
    helper.readById(id)
  }

  override suspend fun add(contact: TrustedContact) = withContext(Dispatchers.IO) {
    validate(contact)
    helper.writableDatabase.insertOrThrow(ContactDatabase.TABLE, null, contact.toValues())
    publish()
  }

  override suspend fun update(contact: TrustedContact) = withContext(Dispatchers.IO) {
    validate(contact)
    check(helper.writableDatabase.update(ContactDatabase.TABLE, contact.toValues(), "id = ?", arrayOf(contact.id)) == 1) {
      "Trusted contact was not found: ${contact.id}"
    }
    publish()
  }

  override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
    require(id.isNotBlank()) { "Contact id must not be blank" }
    helper.writableDatabase.delete(ContactDatabase.TABLE, "id = ?", arrayOf(id))
    publish()
  }

  fun close() {
    ioScope.cancel()
    helper.close()
  }

  private fun publish() {
    state.value = helper.readAll()
  }

  private fun validate(contact: TrustedContact) {
    require(contact.id.isNotBlank()) { "Contact id must not be blank" }
    require(contact.displayName.isNotBlank()) { "Contact display name must not be blank" }
  }

  private companion object {
    const val DEFAULT_DATABASE_NAME = "peykr.db"
  }
}

private fun TrustedContact.toValues() = ContentValues().apply {
  put("id", id)
  put("display_name", displayName)
  put("public_identity", publicIdentity)
  put("pairing_status", pairingStatus.name)
  put("created_at", createdAtEpochMillis)
  put("revoked_at", revokedAtEpochMillis)
}

private class ContactDatabase(context: Context, databaseName: String) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {
  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE $TABLE (id TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL, public_identity TEXT, pairing_status TEXT NOT NULL, created_at INTEGER NOT NULL, revoked_at INTEGER)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    // Future schema changes must use explicit, non-destructive migrations.
  }

  fun readAll(): List<TrustedContact> = readableDatabase.query(TABLE, COLUMNS, null, null, null, null, "created_at ASC").use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.toContact()) }
  }

  fun readById(id: String): TrustedContact? = readableDatabase.query(TABLE, COLUMNS, "id = ?", arrayOf(id), null, null, null, "1").use { cursor ->
    if (cursor.moveToFirst()) cursor.toContact() else null
  }

  private fun android.database.Cursor.toContact() = TrustedContact(
    id = getString(0),
    displayName = getString(1),
    publicIdentity = getStringOrNull(2),
    pairingStatus = runCatching { PairingStatus.valueOf(getString(3)) }.getOrDefault(PairingStatus.NOT_PAIRED),
    createdAtEpochMillis = getLong(4),
    revokedAtEpochMillis = if (isNull(5)) null else getLong(5),
  )

  private fun android.database.Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

  companion object {
    const val TABLE = "trusted_contacts"
    private const val DATABASE_VERSION = 1
    private val COLUMNS = arrayOf("id", "display_name", "public_identity", "pairing_status", "created_at", "revoked_at")
  }
}

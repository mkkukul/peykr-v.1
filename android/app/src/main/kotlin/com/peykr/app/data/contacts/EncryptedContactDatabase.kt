package com.peykr.app.data.contacts

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import com.peykr.app.data.security.DatabaseKeyFailure
import com.peykr.app.data.security.DatabaseKeyException
import com.peykr.app.data.security.DatabaseKeyStore
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

enum class DatabaseState { INITIALIZING, READY, MIGRATION_REQUIRED, MIGRATION_IN_PROGRESS, MIGRATION_FAILED, KEY_MISSING, KEY_INVALID, CORRUPTED }

class DatabaseInitializationException(val state: DatabaseState, message: String, cause: Throwable? = null) : Exception(message, cause)

class EncryptedContactDatabase(private val context: Context, databaseName: String = DATABASE_NAME) {
  private val databaseFile = File(databaseName).takeIf(File::isAbsolute) ?: context.getDatabasePath(databaseName)
  private val keyStore = DatabaseKeyStore(context)
  fun open(): SQLiteDatabase {
    return try {
      System.loadLibrary("sqlcipher")
      val marker = File(context.noBackupFilesDir, MIGRATION_MARKER)
      val metadataExisted = keyStore.hasMetadata()
      if (databaseFile.exists() && !metadataExisted && !isPlaintextReadable(databaseFile)) {
        throw DatabaseInitializationException(DatabaseState.KEY_MISSING, "Encrypted database key metadata is missing")
      }
      val key = keyStore.createOrOpenKey()
      try {
        val databaseWasExisting = databaseFile.exists()
        if (marker.exists()) {
          migrateOrRecover(databaseFile, key, marker)
        } else if (databaseFile.exists() && !metadataExisted) {
          migratePlaintext(databaseFile, key, marker)
        }
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null)
        if (!databaseWasExisting) ensureSchema(database)
        verifyDatabase(database)
        return database
      } finally {
        key.fill(0)
      }
    } catch (error: DatabaseInitializationException) {
      throw error
    } catch (error: DatabaseKeyException) {
      val state = when (error.failure) {
        DatabaseKeyFailure.MISSING_WRAPPING_KEY -> DatabaseState.KEY_MISSING
        DatabaseKeyFailure.MALFORMED_METADATA, DatabaseKeyFailure.INVALID_WRAPPED_KEY -> DatabaseState.KEY_INVALID
      }
      throw DatabaseInitializationException(state, error.message ?: "Database key is unavailable", error)
    } catch (error: Exception) {
      throw DatabaseInitializationException(DatabaseState.KEY_INVALID, "Encrypted database could not be opened", error)
    }
  }

  private fun migrateOrRecover(databaseFile: File, key: ByteArray, marker: File) {
    val temporary = File(databaseFile.parentFile, "$DATABASE_NAME.migration.tmp")
    val plaintextBackup = File(databaseFile.parentFile, "$DATABASE_NAME.plaintext.migration-backup")
    if (databaseFile.exists() && isEncrypted(databaseFile, key)) {
      val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null)
      try {
        verifyDatabase(database)
        cleanupMigrationArtifacts(temporary, plaintextBackup, marker)
      } finally { database.close() }
    } else if (databaseFile.exists()) {
      migratePlaintext(databaseFile, key, marker)
    } else if (!databaseFile.exists() && temporary.exists()) {
      promote(temporary, plaintextBackup, databaseFile)
      val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null)
      try {
        verifyDatabase(database)
        cleanupMigrationArtifacts(temporary, plaintextBackup, marker)
      } finally { database.close() }
    } else {
      throw DatabaseInitializationException(DatabaseState.MIGRATION_FAILED, "Database migration requires manual review")
    }
  }

  private fun migratePlaintext(databaseFile: File, key: ByteArray, marker: File) {
    val temporary = File(databaseFile.parentFile, "$DATABASE_NAME.migration.tmp")
    val plaintextBackup = File(databaseFile.parentFile, "$DATABASE_NAME.plaintext.migration-backup")
    marker.writeText("IN_PROGRESS")
    val source = try { PlainDatabase.openDatabase(databaseFile.path, null, PlainDatabase.OPEN_READONLY) } catch (error: Exception) {
      throw DatabaseInitializationException(DatabaseState.CORRUPTED, "Existing database is not readable", error)
    }
    try {
      if (temporary.exists()) temporary.delete()
      val destination = SQLiteDatabase.openOrCreateDatabase(temporary, key, null, null)
      ensureSchema(destination)
      var copied = 0
      source.query(TABLE, COLUMNS, null, null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) { destination.insertOrThrow(TABLE, null, cursor.toValues()); copied++ }
      }
      destination.close()
      check(copied == count(source)) { "Plaintext migration verification failed" }
      source.close()
      promote(temporary, plaintextBackup, databaseFile)
      val promoted = SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null)
      try { verifyDatabase(promoted) } finally { promoted.close() }
      cleanupMigrationArtifacts(temporary, plaintextBackup, marker)
    } catch (error: Exception) {
      source.close()
      throw DatabaseInitializationException(DatabaseState.MIGRATION_FAILED, "Database migration failed; original data was preserved where possible", error)
    }
  }

  private fun promote(temporary: File, plaintextBackup: File, databaseFile: File) {
    if (databaseFile.exists()) check(databaseFile.renameTo(plaintextBackup)) { "Could not preserve plaintext database" }
    if (!temporary.renameTo(databaseFile)) {
      if (!databaseFile.exists() && plaintextBackup.exists()) plaintextBackup.renameTo(databaseFile)
      throw IllegalStateException("Could not promote encrypted database")
    }
  }

  private fun cleanupMigrationArtifacts(temporary: File, plaintextBackup: File, marker: File) {
    check(!temporary.exists() || temporary.delete()) { "Could not remove migration temporary database" }
    check(!plaintextBackup.exists() || plaintextBackup.delete()) { "Could not remove plaintext migration backup" }
    check(!marker.exists() || marker.delete()) { "Could not remove migration marker" }
  }

  private fun isEncrypted(file: File, key: ByteArray): Boolean = runCatching {
    SQLiteDatabase.openOrCreateDatabase(file, key, null, null).also { it.close() }
    true
  }.getOrDefault(false)

  private fun isPlaintextReadable(file: File): Boolean = runCatching {
    PlainDatabase.openDatabase(file.path, null, PlainDatabase.OPEN_READONLY).use { database ->
      database.query(TABLE, arrayOf("id"), null, null, null, null, null).use { true }
    }
  }.getOrDefault(false)

  private fun ensureSchema(database: SQLiteDatabase) {
    database.execSQL("CREATE TABLE IF NOT EXISTS $TABLE (id TEXT PRIMARY KEY NOT NULL, display_name TEXT NOT NULL, public_identity TEXT, pairing_status TEXT NOT NULL, created_at INTEGER NOT NULL, revoked_at INTEGER)")
  }

  private fun verifyDatabase(database: SQLiteDatabase) {
    database.rawQuery("PRAGMA cipher_integrity_check", null).use { cursor ->
      if (!cursor.moveToFirst() || !cursor.getString(0).equals("ok", ignoreCase = true)) throw DatabaseInitializationException(DatabaseState.CORRUPTED, "SQLCipher integrity check failed")
    }
    database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(TABLE)).use { cursor ->
      if (!cursor.moveToFirst()) throw DatabaseInitializationException(DatabaseState.CORRUPTED, "Trusted contact schema is missing")
    }
    try { database.query(TABLE, COLUMNS, null, null, null, null, "created_at ASC").use { cursor -> cursor.count } }
    catch (error: Exception) { throw DatabaseInitializationException(DatabaseState.CORRUPTED, "Trusted contact query failed", error) }
  }

  private fun count(database: PlainDatabase): Int = database.query(TABLE, arrayOf("id"), null, null, null, null, null).use { it.count }

  private fun Cursor.toValues() = ContentValues().apply {
    put("id", getString(0)); put("display_name", getString(1)); put("public_identity", if (isNull(2)) null else getString(2)); put("pairing_status", getString(3)); put("created_at", getLong(4)); put("revoked_at", if (isNull(5)) null else getLong(5))
  }

  companion object {
    const val DATABASE_NAME = "peykr.db"
    const val TABLE = "trusted_contacts"
    val COLUMNS = arrayOf("id", "display_name", "public_identity", "pairing_status", "created_at", "revoked_at")
    private const val MIGRATION_MARKER = "peykr-db-migration.state"
  }
}

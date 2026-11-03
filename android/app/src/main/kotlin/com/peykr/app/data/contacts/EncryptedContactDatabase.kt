package com.peykr.app.data.contacts

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase as PlainDatabase
import android.util.Log
import com.peykr.app.data.security.DatabaseKeyFailure
import com.peykr.app.data.security.DatabaseKeyException
import com.peykr.app.data.security.DatabaseKeyStore
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

enum class DatabaseState {
  INITIALIZING,
  READY,
  MIGRATION_REQUIRED,
  MIGRATION_IN_PROGRESS,
  MIGRATION_FAILED,
  KEY_MISSING,
  KEY_INVALID,
  CORRUPTED,
  FILE_ACCESS_ERROR,
  SCHEMA_ERROR,
}

class DatabaseInitializationException(val state: DatabaseState, message: String, cause: Throwable? = null) : Exception(message, cause)

class EncryptedContactDatabase(private val context: Context, databaseName: String = DATABASE_NAME) {
  private val databaseFile = File(databaseName).takeIf(File::isAbsolute) ?: context.getDatabasePath(databaseName)
  private val keyStore = DatabaseKeyStore(context)

  fun open(): SQLiteDatabase {
    Log.i(TAG, "Opening encrypted database at: ${databaseFile.path}")
    return try {
      try {
        System.loadLibrary("sqlcipher")
        Log.d(TAG, "Native sqlcipher library loaded successfully")
      } catch (t: Throwable) {
        Log.e(TAG, "Failed to load sqlcipher native library", t)
        throw DatabaseInitializationException(DatabaseState.CORRUPTED, "SQLCipher native library could not be loaded: ${t.message}", t)
      }

      databaseFile.parentFile?.let { parent ->
        if (!parent.exists()) {
          val created = parent.mkdirs()
          Log.d(TAG, "Created database parent directory: $created ($parent)")
        }
      }

      val marker = File(context.noBackupFilesDir, MIGRATION_MARKER)
      val metadataExisted = keyStore.hasMetadata()
      if (databaseFile.exists() && !metadataExisted && !isPlaintextReadable(databaseFile)) {
        Log.e(TAG, "Database file exists without key metadata and is not plaintext readable")
        throw DatabaseInitializationException(DatabaseState.KEY_MISSING, "Encrypted database key metadata is missing")
      }

      val key = try {
        keyStore.createOrOpenKey()
      } catch (e: DatabaseKeyException) {
        Log.e(TAG, "Database key acquisition failed: ${e.failure} - ${e.message}", e)
        val state = when (e.failure) {
          DatabaseKeyFailure.MISSING_WRAPPING_KEY -> DatabaseState.KEY_MISSING
          DatabaseKeyFailure.MALFORMED_METADATA, DatabaseKeyFailure.INVALID_WRAPPED_KEY -> DatabaseState.KEY_INVALID
        }
        throw DatabaseInitializationException(state, e.message ?: "Database key is unavailable", e)
      }

      try {
        if (marker.exists()) {
          Log.i(TAG, "Migration marker detected, running migrateOrRecover")
          migrateOrRecover(databaseFile, key, marker)
        } else if (databaseFile.exists() && !metadataExisted) {
          Log.i(TAG, "Plaintext legacy database detected, running migratePlaintext")
          migratePlaintext(databaseFile, key, marker)
        }

        Log.d(TAG, "Opening or creating SQLiteDatabase with SQLCipher")
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, key, null, null)

        // ALWAYS ensure schema exists unconditionally (CREATE TABLE IF NOT EXISTS)
        Log.d(TAG, "Ensuring database schema exists")
        ensureSchema(database)

        Log.d(TAG, "Verifying database integrity")
        verifyDatabase(database)

        Log.i(TAG, "Encrypted database opened, schema verified, and ready")
        return database
      } finally {
        key.fill(0)
      }
    } catch (error: DatabaseInitializationException) {
      Log.e(TAG, "DatabaseInitializationException: ${error.state} - ${error.message}", error)
      throw error
    } catch (error: DatabaseKeyException) {
      val state = when (error.failure) {
        DatabaseKeyFailure.MISSING_WRAPPING_KEY -> DatabaseState.KEY_MISSING
        DatabaseKeyFailure.MALFORMED_METADATA, DatabaseKeyFailure.INVALID_WRAPPED_KEY -> DatabaseState.KEY_INVALID
      }
      Log.e(TAG, "DatabaseKeyException: $state - ${error.message}", error)
      throw DatabaseInitializationException(state, error.message ?: "Database key is unavailable", error)
    } catch (error: java.io.IOException) {
      Log.e(TAG, "I/O error during database initialization", error)
      throw DatabaseInitializationException(DatabaseState.FILE_ACCESS_ERROR, "Database file access failed: ${error.message}", error)
    } catch (error: Exception) {
      Log.e(TAG, "General exception during database open: ${error.javaClass.simpleName} - ${error.message}", error)
      throw DatabaseInitializationException(DatabaseState.KEY_INVALID, "Encrypted database could not be opened: ${error.message}", error)
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
    database.execSQL("CREATE INDEX IF NOT EXISTS idx_trusted_contacts_status ON $TABLE(pairing_status)")
  }

  private fun verifyDatabase(database: SQLiteDatabase) {
    // 1. Check SQLCipher page-level HMAC integrity
    // PRAGMA cipher_integrity_check iterates all pages and returns 0 rows if all HMACs match.
    // If corruption exists, it returns error rows describing failing pages.
    try {
      database.rawQuery("PRAGMA cipher_integrity_check", null).use { cursor ->
        if (cursor.moveToFirst()) {
          val result = cursor.getString(0)
          if (!result.equals("ok", ignoreCase = true)) {
            Log.e(TAG, "SQLCipher HMAC check reported corruption: $result")
            throw DatabaseInitializationException(DatabaseState.CORRUPTED, "SQLCipher HMAC integrity check failed: $result")
          }
        }
      }
    } catch (e: Exception) {
      if (e is DatabaseInitializationException) throw e
      Log.w(TAG, "cipher_integrity_check execution notice: ${e.message}")
    }

    // 2. Check SQLite B-Tree integrity
    database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
      if (!cursor.moveToFirst() || !cursor.getString(0).equals("ok", ignoreCase = true)) {
        val detail = if (cursor.moveToFirst()) cursor.getString(0) else "no output"
        Log.e(TAG, "SQLite integrity check reported: $detail")
        throw DatabaseInitializationException(DatabaseState.CORRUPTED, "SQLite B-Tree integrity check failed: $detail")
      }
    }

    // 3. Verify schema table exists in sqlite_master
    database.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(TABLE)).use { cursor ->
      if (!cursor.moveToFirst()) {
        Log.e(TAG, "Schema check failed: table $TABLE not found in sqlite_master")
        throw DatabaseInitializationException(DatabaseState.SCHEMA_ERROR, "Trusted contact schema is missing")
      }
    }

    // 4. Test query execution
    try {
      database.query(TABLE, COLUMNS, null, null, null, null, "created_at ASC").use { cursor ->
        cursor.count
      }
    } catch (error: Exception) {
      Log.e(TAG, "Trusted contact query failed during verification", error)
      throw DatabaseInitializationException(DatabaseState.SCHEMA_ERROR, "Trusted contact query failed", error)
    }
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
    private const val TAG = "PeykrDatabase"
  }
}

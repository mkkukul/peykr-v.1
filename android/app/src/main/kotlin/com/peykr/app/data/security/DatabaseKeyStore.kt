package com.peykr.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

enum class DatabaseKeyFailure { MISSING_WRAPPING_KEY, MALFORMED_METADATA, INVALID_WRAPPED_KEY }

class DatabaseKeyException(val failure: DatabaseKeyFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

class DatabaseKeyStore(context: Context) {
  private val directory = context.noBackupFilesDir
  private val metadataFile = File(directory, "peykr-db-key.bin")
  private val alias = "peykr.database.wrap.v1"

  fun hasMetadata(): Boolean = metadataFile.exists()

  fun createOrOpenKey(): ByteArray = try {
    if (metadataFile.exists()) unwrap(readMetadata()) else createAndStore()
  } catch (error: DatabaseKeyException) {
    throw error
  } catch (error: java.io.EOFException) {
    throw DatabaseKeyException(DatabaseKeyFailure.MALFORMED_METADATA, "Database key metadata is truncated", error)
  } catch (error: javax.crypto.AEADBadTagException) {
    throw DatabaseKeyException(DatabaseKeyFailure.INVALID_WRAPPED_KEY, "Database key metadata authentication failed", error)
  } catch (error: Exception) {
    throw DatabaseKeyException(DatabaseKeyFailure.MALFORMED_METADATA, "Database encryption key is unavailable", error)
  }

  private fun createAndStore(): ByteArray {
    val databaseKey = ByteArray(32).also(SecureRandom()::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
    writeMetadataAtomically(Metadata(cipher.iv, cipher.doFinal(databaseKey)))
    return databaseKey
  }

  private fun unwrap(metadata: Metadata): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, metadata.iv))
    return cipher.doFinal(metadata.ciphertext)
  }

  private fun wrappingKey(): java.security.Key {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (!keyStore.containsAlias(alias)) {
      if (metadataFile.exists()) throw DatabaseKeyException(DatabaseKeyFailure.MISSING_WRAPPING_KEY, "Database wrapping key is missing")
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setRandomizedEncryptionRequired(true)
          .build())
      }.generateKey()
    }
    return keyStore.getKey(alias, null) ?: throw DatabaseKeyException(DatabaseKeyFailure.MISSING_WRAPPING_KEY, "Database wrapping key is missing")
  }

  private fun readMetadata(): Metadata = DataInputStream(metadataFile.inputStream()).use { input ->
    check(input.readInt() == FORMAT_VERSION) { "Unsupported database key metadata" }
    val ivLength = input.readInt()
    require(ivLength in 12..32) { "Invalid database key metadata IV" }
    val iv = ByteArray(ivLength)
    input.readFully(iv)
    val ciphertextLength = input.readInt()
    require(ciphertextLength in 32..128) { "Invalid database key metadata ciphertext" }
    val ciphertext = ByteArray(ciphertextLength)
    input.readFully(ciphertext)
    Metadata(iv, ciphertext)
  }

  private fun writeMetadataAtomically(metadata: Metadata) {
    val temporary = File(directory, "peykr-db-key.bin.tmp")
    FileOutputStream(temporary).use { fileOutput ->
      DataOutputStream(fileOutput).use { output ->
      output.writeInt(FORMAT_VERSION)
      output.writeInt(metadata.iv.size)
      output.write(metadata.iv)
      output.writeInt(metadata.ciphertext.size)
      output.write(metadata.ciphertext)
      fileOutput.fd.sync()
      }
    }
    check(temporary.renameTo(metadataFile)) { "Could not promote database key metadata" }
  }

  private data class Metadata(val iv: ByteArray, val ciphertext: ByteArray)
  private companion object { const val FORMAT_VERSION = 1 }
}

package com.peykr.app.core.security

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

data class CiphertextWithIv(
  val iv: ByteArray,
  val ciphertextWithTag: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CiphertextWithIv) return false
    return iv.contentEquals(other.iv) && ciphertextWithTag.contentEquals(other.ciphertextWithTag)
  }

  override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertextWithTag.contentHashCode()
}

object CryptoPrimitives {
  private val secureRandom = SecureRandom()
  private const val GCM_IV_BYTES = 12
  private const val GCM_TAG_BITS = 128
  private const val HMAC_ALGORITHM = "HmacSHA256"

  fun generateEcdhKeyPair(): KeyPair {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
    return kpg.generateKeyPair()
  }

  fun computeSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): ByteArray {
    val ka = KeyAgreement.getInstance("ECDH")
    ka.init(privateKey)
    ka.doPhase(peerPublicKey, true)
    return ka.generateSecret()
  }

  fun encryptAesGcm(
    key: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray = ByteArray(0),
  ): CiphertextWithIv {
    val iv = ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val keySpec = SecretKeySpec(key, "AES")
    val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
    if (associatedData.isNotEmpty()) {
      cipher.updateAAD(associatedData)
    }
    val ciphertextWithTag = cipher.doFinal(plaintext)
    return CiphertextWithIv(iv, ciphertextWithTag)
  }

  fun decryptAesGcm(
    key: ByteArray,
    iv: ByteArray,
    ciphertextWithTag: ByteArray,
    associatedData: ByteArray = ByteArray(0),
  ): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val keySpec = SecretKeySpec(key, "AES")
    val gcmSpec = GCMParameterSpec(GCM_TAG_BITS, iv)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
    if (associatedData.isNotEmpty()) {
      cipher.updateAAD(associatedData)
    }
    return cipher.doFinal(ciphertextWithTag)
  }

  fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
    val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(actualSalt, HMAC_ALGORITHM))
    return mac.doFinal(ikm)
  }

  fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
    val iterations = ceil(length.toDouble() / 32.0).toInt()
    require(iterations <= 255) { "Requested HKDF length is too large" }

    val output = ByteArray(length)
    var t = ByteArray(0)
    var offset = 0

    for (i in 1..iterations) {
      mac.update(t)
      mac.update(info)
      mac.update(i.toByte())
      t = mac.doFinal()
      val toCopy = (length - offset).coerceAtMost(t.size)
      System.arraycopy(t, 0, output, offset, toCopy)
      offset += toCopy
    }
    return output
  }

  fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
    val prk = hkdfExtract(salt, ikm)
    return hkdfExpand(prk, info, length)
  }
}

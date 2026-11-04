package com.peykr.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class DoubleRatchetEngineTest {

  private val engine = DoubleRatchetEngine()

  @Test
  fun initiatorAndResponder_bidirectionalMessaging_ratchetsSuccessfully() {
    val masterSharedSecret = ByteArray(32) { (it + 7).toByte() }

    // Bob creates his initial prekey
    val bobInitialKeyPair = CryptoPrimitives.generateEcdhKeyPair()

    // Alice initializes with Bob's public key
    var aliceState = engine.initializeInitiator(
      contactId = "bob",
      sharedMasterSecret = masterSharedSecret,
      remoteDhPublicKey = bobInitialKeyPair.public,
    )

    // Bob initializes as responder
    var bobState = engine.initializeResponder(
      contactId = "alice",
      sharedMasterSecret = masterSharedSecret,
      localKeyPair = bobInitialKeyPair,
    )

    // 1. Alice sends message to Bob
    val msg1Text = "Hello Bob from Alice!"
    val (aliceAfter1, enc1) = engine.ratchetEncrypt(aliceState, msg1Text.toByteArray(StandardCharsets.UTF_8))
    aliceState = aliceAfter1

    val (bobAfter1, dec1) = engine.ratchetDecrypt(bobState, enc1)
    bobState = bobAfter1
    assertEquals(msg1Text, String(dec1, StandardCharsets.UTF_8))

    // 2. Bob replies to Alice (triggers DH ratchet forward)
    val msg2Text = "Hey Alice, receiving loud and clear!"
    val (bobAfter2, enc2) = engine.ratchetEncrypt(bobState, msg2Text.toByteArray(StandardCharsets.UTF_8))
    bobState = bobAfter2

    val (aliceAfter2, dec2) = engine.ratchetDecrypt(aliceState, enc2)
    aliceState = aliceAfter2
    assertEquals(msg2Text, String(dec2, StandardCharsets.UTF_8))

    // 3. Alice sends a second message
    val msg3Text = "Forward secrecy confirmed."
    val (aliceAfter3, enc3) = engine.ratchetEncrypt(aliceState, msg3Text.toByteArray(StandardCharsets.UTF_8))
    aliceState = aliceAfter3

    val (bobAfter3, dec3) = engine.ratchetDecrypt(bobState, enc3)
    bobState = bobAfter3
    assertEquals(msg3Text, String(dec3, StandardCharsets.UTF_8))
  }

  @Test
  fun tamperedMessage_failsDecryption() {
    val masterSharedSecret = ByteArray(32) { 42 }
    val bobInitialKeyPair = CryptoPrimitives.generateEcdhKeyPair()

    var aliceState = engine.initializeInitiator("bob", masterSharedSecret, bobInitialKeyPair.public)
    val bobState = engine.initializeResponder("alice", masterSharedSecret, bobInitialKeyPair)

    val (_, enc) = engine.ratchetEncrypt(aliceState, "Secret text".toByteArray())

    // Tamper with ciphertext
    val rawBytes = java.util.Base64.getDecoder().decode(enc.ciphertextWithTagBase64)
    rawBytes[0] = (rawBytes[0].toInt() xor 0xFF).toByte()
    val tamperedEnc = enc.copy(ciphertextWithTagBase64 = java.util.Base64.getEncoder().encodeToString(rawBytes))

    val result = runCatching { engine.ratchetDecrypt(bobState, tamperedEnc) }
    assertTrue(result.isFailure)
  }
}

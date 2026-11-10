package com.peykr.app.domain.messaging

import com.peykr.app.core.identity.DeviceIdentity
import com.peykr.app.core.identity.DeviceIdentityInfo
import com.peykr.app.core.identity.DeviceIdentityResult
import com.peykr.app.core.identity.DeviceIdentityState
import com.peykr.app.core.security.CryptoPrimitives
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

private class TestRepo : MessagingRepository {
  val messages = mutableListOf<Message>()
  private val flow = MutableStateFlow<List<Message>>(emptyList())

  override fun getConversations(): Flow<List<Conversation>> = MutableStateFlow(emptyList())
  override fun getMessages(conversationId: String): Flow<List<Message>> = flow
  override suspend fun saveConversation(conversation: Conversation) {}
  override suspend fun saveMessage(message: Message) {
    messages.add(message)
    flow.value = messages.toList()
  }
  override suspend fun updateMessageStatus(messageId: String, status: MessageStatus) {}
  override suspend fun updateMessageDelivery(
    messageId: String,
    status: MessageStatus,
    transportType: MessageTransportType,
    deliveryTime: Long?
  ) {}
  override suspend fun deleteMessage(messageId: String) {}
  override suspend fun deleteConversation(conversationId: String) {}
  override suspend fun getGroupMembers(conversationId: String): List<GroupMember> = emptyList()
  override suspend fun addGroupMember(member: GroupMember) {}
  override suspend fun removeGroupMember(conversationId: String, contactId: String) {}
}

private class TestIdentity(
  private val deviceId: String,
  private val publicKeyB64: String,
) : DeviceIdentity {
  override val identityState: StateFlow<DeviceIdentityState> =
    MutableStateFlow(DeviceIdentityState.Ready(DeviceIdentityInfo(deviceId, publicKeyB64)))

  override fun initialize() {}
  override fun retry() {}
  override fun getOrCreate(): DeviceIdentityResult =
    DeviceIdentityResult.Success(DeviceIdentityInfo(deviceId, publicKeyB64))

  override fun sign(transcript: ByteArray): Result<ByteArray> = Result.success(ByteArray(64))
  override fun verify(publicKeyBase64: String, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun verify(publicKey: java.security.PublicKey, transcript: ByteArray, signature: ByteArray): Boolean = true
  override fun close() {}
}

class MessagingEngineDoubleRatchetTest {

  @Test
  fun testBidirectionalDoubleRatchetMessagingBetweenTwoDistinctDevices() = runTest {
    // Generate distinct EC identity keys for Device A (Alice) and Device B (Bob)
    val aliceEcKey = CryptoPrimitives.generateEcdhKeyPair()
    val bobEcKey = CryptoPrimitives.generateEcdhKeyPair()

    val alicePubKeyB64 = Base64.getEncoder().encodeToString(aliceEcKey.public.encoded)
    val bobPubKeyB64 = Base64.getEncoder().encodeToString(bobEcKey.public.encoded)

    val aliceId = "alice_device_001"
    val bobId = "bob_device_002"

    val aliceRepo = TestRepo()
    val bobRepo = TestRepo()

    val aliceEngine = MessagingEngine(TestIdentity(aliceId, alicePubKeyB64), aliceRepo)
    val bobEngine = MessagingEngine(TestIdentity(bobId, bobPubKeyB64), bobRepo)

    // 1. Alice sends first message to Bob
    val msg1Text = "Merhaba Bob! Uçtan uca şifreli ilk mesaj."
    val encResult1 = aliceEngine.sendTextMessage(
      conversationId = "conv_$bobId",
      recipientId = bobId,
      text = msg1Text,
      recipientPublicKeyBase64 = bobPubKeyB64,
    )
    assertTrue("Alice must encrypt successfully", encResult1.isSuccess)
    val (aliceMsg1, encryptedPayload1) = encResult1.getOrThrow()
    assertTrue("Encrypted ratchet payload must be present", encryptedPayload1 != null)

    // Bob receives and decrypts Alice's first message
    val decResult1 = bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_$aliceId",
      senderId = aliceId,
      encryptedMessage = encryptedPayload1!!,
      senderPublicKeyBase64 = alicePubKeyB64,
    )
    assertTrue("Bob must decrypt successfully: ${decResult1.exceptionOrNull()?.message}", decResult1.isSuccess)
    val bobReceivedMsg1 = decResult1.getOrThrow()
    assertEquals(msg1Text, bobReceivedMsg1.textContent)

    // 2. Alice sends a second message before Bob replies (testing chain key stepping)
    val msg2Text = "İkinci mesaj: Zincir anahtarı simetrik ratchet ile ilerlemeli."
    val encResult2 = aliceEngine.sendTextMessage(
      conversationId = "conv_$bobId",
      recipientId = bobId,
      text = msg2Text,
      recipientPublicKeyBase64 = bobPubKeyB64,
    )
    assertTrue(encResult2.isSuccess)
    val (_, encryptedPayload2) = encResult2.getOrThrow()

    val decResult2 = bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_$aliceId",
      senderId = aliceId,
      encryptedMessage = encryptedPayload2!!,
      senderPublicKeyBase64 = alicePubKeyB64,
    )
    assertTrue("Bob must decrypt consecutive message: ${decResult2.exceptionOrNull()?.message}", decResult2.isSuccess)
    assertEquals(msg2Text, decResult2.getOrThrow().textContent)

    // 3. Bob replies to Alice (triggers DH ratchet forward step on Bob)
    val replyText1 = "Aleyküm selam Alice! Cevap mesajı DH ratchet ile şifrelendi."
    val bobReplyResult1 = bobEngine.sendTextMessage(
      conversationId = "conv_$aliceId",
      recipientId = aliceId,
      text = replyText1,
      recipientPublicKeyBase64 = alicePubKeyB64,
    )
    assertTrue("Bob must send reply successfully", bobReplyResult1.isSuccess)
    val (_, bobEncPayload1) = bobReplyResult1.getOrThrow()

    // Alice receives Bob's reply (triggers DH ratchet on Alice and decrypts)
    val aliceDecReplyResult1 = aliceEngine.receiveEncryptedTextMessage(
      conversationId = "conv_$bobId",
      senderId = bobId,
      encryptedMessage = bobEncPayload1!!,
      senderPublicKeyBase64 = bobPubKeyB64,
    )
    assertTrue("Alice must decrypt Bob's reply: ${aliceDecReplyResult1.exceptionOrNull()?.message}", aliceDecReplyResult1.isSuccess)
    assertEquals(replyText1, aliceDecReplyResult1.getOrThrow().textContent)

    // 4. Alice replies to Bob (another round of DH ratchet)
    val replyText2 = "Harika, DH ratchet iki yönde de kusursuz çalışıyor!"
    val aliceReplyResult2 = aliceEngine.sendTextMessage(
      conversationId = "conv_$bobId",
      recipientId = bobId,
      text = replyText2,
      recipientPublicKeyBase64 = bobPubKeyB64,
    )
    assertTrue(aliceReplyResult2.isSuccess)
    val (_, aliceEncPayload2) = aliceReplyResult2.getOrThrow()

    val bobDecReplyResult2 = bobEngine.receiveEncryptedTextMessage(
      conversationId = "conv_$aliceId",
      senderId = aliceId,
      encryptedMessage = aliceEncPayload2!!,
      senderPublicKeyBase64 = alicePubKeyB64,
    )
    assertTrue("Bob must decrypt Alice's second turn message: ${bobDecReplyResult2.exceptionOrNull()?.message}", bobDecReplyResult2.isSuccess)
    assertEquals(replyText2, bobDecReplyResult2.getOrThrow().textContent)
  }
}

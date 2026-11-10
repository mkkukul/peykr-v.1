package com.peykr.app.domain.messaging

import kotlinx.coroutines.flow.Flow

enum class MessageStatus {
  PENDING,
  QUEUED_OFFLINE,
  SENDING,
  SENT,
  DELIVERED,
  READ,
  FAILED
}

enum class MessageTransportType {
  DIRECT_P2P,
  LOCAL_WIFI,
  BLUETOOTH_MESH,
  ONLINE_RELAY,
  OFFLINE_QUEUED
}

enum class MessageDirection {
  OUTGOING,
  INCOMING
}

data class AttachmentMetadata(
  val uri: String,
  val fileName: String,
  val mimeType: String,
  val sizeBytes: Long,
  val sha256Checksum: String,
)

data class Message(
  val id: String,
  val conversationId: String,
  val senderId: String,
  val recipientId: String,
  val textContent: String?,
  val attachment: AttachmentMetadata? = null,
  val status: MessageStatus = MessageStatus.SENT,
  val direction: MessageDirection = MessageDirection.OUTGOING,
  val timestamp: Long = System.currentTimeMillis(),
  val isRead: Boolean = true,
  val transportType: MessageTransportType = MessageTransportType.DIRECT_P2P,
  val verifiedDeliveryEpochMillis: Long? = null,
)

data class Conversation(
  val id: String,
  val title: String,
  val isGroup: Boolean,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  val lastMessage: String? = null,
  val unreadCount: Int = 0,
)

data class GroupMember(
  val conversationId: String,
  val contactId: String,
  val role: String = "MEMBER",
  val joinedAt: Long = System.currentTimeMillis(),
)

interface MessagingRepository {
  fun getConversations(): Flow<List<Conversation>>
  fun getMessages(conversationId: String): Flow<List<Message>>
  suspend fun saveConversation(conversation: Conversation)
  suspend fun saveMessage(message: Message)
  suspend fun updateMessageStatus(messageId: String, status: MessageStatus)
  suspend fun updateMessageDelivery(
    messageId: String,
    status: MessageStatus,
    transportType: MessageTransportType,
    deliveryTime: Long? = System.currentTimeMillis()
  )
  suspend fun deleteMessage(messageId: String)
  suspend fun deleteConversation(conversationId: String)
  suspend fun getMessageById(messageId: String): Message? = null
  suspend fun getGroupMembers(conversationId: String): List<GroupMember>
  suspend fun addGroupMember(member: GroupMember)
  suspend fun removeGroupMember(conversationId: String, contactId: String)
}

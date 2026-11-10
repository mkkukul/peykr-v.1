package com.peykr.app.data.messaging

import android.content.ContentValues
import android.content.Context
import com.peykr.app.data.contacts.EncryptedContactDatabase
import com.peykr.app.domain.messaging.AttachmentMetadata
import com.peykr.app.domain.messaging.Conversation
import com.peykr.app.domain.messaging.GroupMember
import com.peykr.app.domain.messaging.Message
import com.peykr.app.domain.messaging.MessageDirection
import com.peykr.app.domain.messaging.MessageStatus
import com.peykr.app.domain.messaging.MessageTransportType
import com.peykr.app.domain.messaging.MessagingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SQLiteDatabase

class SqliteMessagingRepository(
  private val context: Context,
  private val encryptedDatabase: EncryptedContactDatabase = EncryptedContactDatabase(context),
) : MessagingRepository {

  private var dbInstance: SQLiteDatabase? = null
  private val databaseLock = Any()

  private val conversationsFlow = MutableStateFlow<List<Conversation>>(emptyList())
  private val messageFlows = mutableMapOf<String, MutableStateFlow<List<Message>>>()

  private fun getDatabase(): SQLiteDatabase {
    synchronized(databaseLock) {
      if (dbInstance == null || !dbInstance!!.isOpen) {
        dbInstance = encryptedDatabase.open()
      }
      return dbInstance!!
    }
  }

  override fun getConversations(): Flow<List<Conversation>> {
    refreshConversations()
    return conversationsFlow.asStateFlow()
  }

  override fun getMessages(conversationId: String): Flow<List<Message>> {
    val flow = synchronized(messageFlows) {
      messageFlows.computeIfAbsent(conversationId) { MutableStateFlow(emptyList()) }
    }
    refreshMessages(conversationId)
    return flow.asStateFlow()
  }

  override suspend fun saveConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
    val db = getDatabase()
    val values = ContentValues().apply {
      put("id", conversation.id)
      put("title", conversation.title)
      put("is_group", if (conversation.isGroup) 1 else 0)
      put("created_at", conversation.createdAt)
      put("updated_at", conversation.updatedAt)
      put("last_message", conversation.lastMessage)
      put("unread_count", conversation.unreadCount)
    }
    db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    refreshConversations()
  }

  override suspend fun saveMessage(message: Message) = withContext(Dispatchers.IO) {
    val db = getDatabase()
    val values = ContentValues().apply {
      put("id", message.id)
      put("conversation_id", message.conversationId)
      put("sender_id", message.senderId)
      put("recipient_id", message.recipientId)
      put("text_content", message.textContent)
      put("attachment_uri", message.attachment?.uri)
      put("attachment_name", message.attachment?.fileName)
      put("attachment_type", message.attachment?.mimeType)
      put("attachment_size", message.attachment?.sizeBytes ?: 0L)
      put("attachment_hash", message.attachment?.sha256Checksum)
      put("status", message.status.name)
      put("direction", message.direction.name)
      put("timestamp", message.timestamp)
      put("is_read", if (message.isRead) 1 else 0)
      put("transport_type", message.transportType.name)
      put("verified_delivery_time", message.verifiedDeliveryEpochMillis)
    }
    db.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)

    // Update conversation last message & updated_at
    val preview = message.textContent ?: message.attachment?.fileName ?: "Attachment"
    val updateValues = ContentValues().apply {
      put("last_message", preview)
      put("updated_at", message.timestamp)
    }
    db.update("conversations", updateValues, "id = ?", arrayOf(message.conversationId))

    refreshConversations()
    refreshMessages(message.conversationId)
  }

  override suspend fun updateMessageStatus(messageId: String, status: MessageStatus): Unit = withContext(Dispatchers.IO) {
    val db = getDatabase()
    val values = ContentValues().apply {
      put("status", status.name)
    }
    db.update("messages", values, "id = ?", arrayOf(messageId))

    // Query conversation ID to refresh
    var convId: String? = null
    db.query("messages", arrayOf("conversation_id"), "id = ?", arrayOf(messageId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        convId = cursor.getString(0)
      }
    }
    convId?.let { refreshMessages(it) }
    Unit
  }

  override suspend fun updateMessageDelivery(
    messageId: String,
    status: MessageStatus,
    transportType: MessageTransportType,
    deliveryTime: Long?
  ): Unit = withContext(Dispatchers.IO) {
    val db = getDatabase()
    val values = ContentValues().apply {
      put("status", status.name)
      put("transport_type", transportType.name)
      put("verified_delivery_time", deliveryTime)
    }
    db.update("messages", values, "id = ?", arrayOf(messageId))

    var convId: String? = null
    db.query("messages", arrayOf("conversation_id"), "id = ?", arrayOf(messageId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        convId = cursor.getString(0)
      }
    }
    convId?.let { refreshMessages(it) }
    Unit
  }

  override suspend fun deleteMessage(messageId: String) = withContext(Dispatchers.IO) {
    val db = getDatabase()
    var convId: String? = null
    db.query("messages", arrayOf("conversation_id"), "id = ?", arrayOf(messageId), null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        convId = cursor.getString(0)
      }
    }
    db.delete("messages", "id = ?", arrayOf(messageId))
    convId?.let { refreshMessages(it) }
    refreshConversations()
  }

  override suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
    val db = getDatabase()
    db.delete("messages", "conversation_id = ?", arrayOf(conversationId))
    db.delete("conversation_members", "conversation_id = ?", arrayOf(conversationId))
    db.delete("conversations", "id = ?", arrayOf(conversationId))
    refreshConversations()
    synchronized(messageFlows) {
      messageFlows.remove(conversationId)?.value = emptyList()
    }
  }

  override suspend fun getMessageById(messageId: String): Message? = withContext(Dispatchers.IO) {
    try {
      val db = getDatabase()
      db.query("messages", null, "id = ?", arrayOf(messageId), null, null, null).use { cursor ->
        if (cursor.moveToFirst()) {
          val uri = if (cursor.isNull(5)) null else cursor.getString(5)
          val attachment = if (uri != null) {
            AttachmentMetadata(
              uri = uri,
              fileName = cursor.getString(6) ?: "attachment",
              mimeType = cursor.getString(7) ?: "application/octet-stream",
              sizeBytes = cursor.getLong(8),
              sha256Checksum = cursor.getString(9) ?: "",
            )
          } else null
          val transportCol = cursor.getColumnIndex("transport_type")
          val deliveryCol = cursor.getColumnIndex("verified_delivery_time")
          val transportStr = if (transportCol >= 0 && !cursor.isNull(transportCol)) cursor.getString(transportCol) else null
          val transportType = runCatching { MessageTransportType.valueOf(transportStr!!) }.getOrDefault(MessageTransportType.DIRECT_P2P)
          val verifiedTime = if (deliveryCol >= 0 && !cursor.isNull(deliveryCol)) cursor.getLong(deliveryCol) else null

          Message(
            id = cursor.getString(0),
            conversationId = cursor.getString(1),
            senderId = cursor.getString(2),
            recipientId = cursor.getString(3),
            textContent = cursor.getString(4),
            attachment = attachment,
            status = runCatching { MessageStatus.valueOf(cursor.getString(10)) }.getOrDefault(MessageStatus.SENT),
            direction = runCatching { MessageDirection.valueOf(cursor.getString(11)) }.getOrDefault(MessageDirection.OUTGOING),
            timestamp = cursor.getLong(12),
            isRead = cursor.getInt(13) == 1,
            transportType = transportType,
            verifiedDeliveryEpochMillis = verifiedTime,
          )
        } else null
      }
    } catch (_: Exception) {
      null
    }
  }

  override suspend fun getGroupMembers(conversationId: String): List<GroupMember> = withContext(Dispatchers.IO) {
    val db = getDatabase()
    val members = mutableListOf<GroupMember>()
    db.query(
      "conversation_members",
      arrayOf("conversation_id", "contact_id", "role", "joined_at"),
      "conversation_id = ?",
      arrayOf(conversationId),
      null, null, "joined_at ASC"
    ).use { cursor ->
      while (cursor.moveToNext()) {
        members.add(
          GroupMember(
            conversationId = cursor.getString(0),
            contactId = cursor.getString(1),
            role = cursor.getString(2),
            joinedAt = cursor.getLong(3),
          )
        )
      }
    }
    members
  }

  override suspend fun addGroupMember(member: GroupMember): Unit = withContext(Dispatchers.IO) {
    val db = getDatabase()
    val values = ContentValues().apply {
      put("conversation_id", member.conversationId)
      put("contact_id", member.contactId)
      put("role", member.role)
      put("joined_at", member.joinedAt)
    }
    db.insertWithOnConflict("conversation_members", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    Unit
  }

  override suspend fun removeGroupMember(conversationId: String, contactId: String): Unit = withContext(Dispatchers.IO) {
    val db = getDatabase()
    db.delete(
      "conversation_members",
      "conversation_id = ? AND contact_id = ?",
      arrayOf(conversationId, contactId)
    )
    Unit
  }

  private fun refreshConversations() {
    try {
      val db = getDatabase()
      val list = mutableListOf<Conversation>()
      db.query(
        "conversations",
        arrayOf("id", "title", "is_group", "created_at", "updated_at", "last_message", "unread_count"),
        null, null, null, null, "updated_at DESC"
      ).use { cursor ->
        while (cursor.moveToNext()) {
          list.add(
            Conversation(
              id = cursor.getString(0),
              title = cursor.getString(1),
              isGroup = cursor.getInt(2) == 1,
              createdAt = cursor.getLong(3),
              updatedAt = cursor.getLong(4),
              lastMessage = cursor.getString(5),
              unreadCount = cursor.getInt(6),
            )
          )
        }
      }
      conversationsFlow.value = list
    } catch (e: Exception) {
      // Handled during initial setup
    }
  }

  private fun refreshMessages(conversationId: String) {
    try {
      val db = getDatabase()
      val list = mutableListOf<Message>()
      db.query(
        "messages",
        null,
        "conversation_id = ?",
        arrayOf(conversationId),
        null, null, "timestamp ASC"
      ).use { cursor ->
        val transportCol = cursor.getColumnIndex("transport_type")
        val deliveryCol = cursor.getColumnIndex("verified_delivery_time")

        while (cursor.moveToNext()) {
          val uri = if (cursor.isNull(5)) null else cursor.getString(5)
          val attachment = if (uri != null) {
            AttachmentMetadata(
              uri = uri,
              fileName = cursor.getString(6) ?: "attachment",
              mimeType = cursor.getString(7) ?: "application/octet-stream",
              sizeBytes = cursor.getLong(8),
              sha256Checksum = cursor.getString(9) ?: "",
            )
          } else null

          val transportStr = if (transportCol >= 0 && !cursor.isNull(transportCol)) cursor.getString(transportCol) else null
          val transportType = runCatching { MessageTransportType.valueOf(transportStr!!) }
            .getOrDefault(MessageTransportType.DIRECT_P2P)

          val verifiedTime = if (deliveryCol >= 0 && !cursor.isNull(deliveryCol)) cursor.getLong(deliveryCol) else null

          list.add(
            Message(
              id = cursor.getString(0),
              conversationId = cursor.getString(1),
              senderId = cursor.getString(2),
              recipientId = cursor.getString(3),
              textContent = cursor.getString(4),
              attachment = attachment,
              status = runCatching { MessageStatus.valueOf(cursor.getString(10)) }.getOrDefault(MessageStatus.SENT),
              direction = runCatching { MessageDirection.valueOf(cursor.getString(11)) }.getOrDefault(MessageDirection.OUTGOING),
              timestamp = cursor.getLong(12),
              isRead = cursor.getInt(13) == 1,
              transportType = transportType,
              verifiedDeliveryEpochMillis = verifiedTime,
            )
          )
        }
      }
      synchronized(messageFlows) {
        messageFlows[conversationId]?.value = list
      }
    } catch (e: Exception) {
      // Handled
    }
  }

  fun close() {
    synchronized(databaseLock) {
      dbInstance?.close()
      dbInstance = null
    }
  }
}

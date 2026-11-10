package com.peykr.app.core.security

import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

interface RatchetStore {
  fun saveSession(contactId: String, state: RatchetSessionState)
  fun loadSession(contactId: String): RatchetSessionState?
  fun removeSession(contactId: String)
}

class InMemoryRatchetStore : RatchetStore {
  private val store = ConcurrentHashMap<String, RatchetSessionState>()
  override fun saveSession(contactId: String, state: RatchetSessionState) { store[contactId] = state }
  override fun loadSession(contactId: String): RatchetSessionState? = store[contactId]
  override fun removeSession(contactId: String) { store.remove(contactId) }
}

class FileRatchetStore(private val baseDir: File) : RatchetStore {
  init {
    if (!baseDir.exists()) {
      baseDir.mkdirs()
    }
  }

  override fun saveSession(contactId: String, state: RatchetSessionState) {
    runCatching {
      val json = JSONObject().apply {
        put("contactId", state.contactId)
        put("rootKey", Base64.getEncoder().encodeToString(state.rootKey))
        put("sendingChainKey", state.sendingChainKey?.let { Base64.getEncoder().encodeToString(it) })
        put("receivingChainKey", state.receivingChainKey?.let { Base64.getEncoder().encodeToString(it) })
        put("localDhPublic", Base64.getEncoder().encodeToString(state.localDhKeyPair.public.encoded))
        put("localDhPrivate", Base64.getEncoder().encodeToString(state.localDhKeyPair.private.encoded))
        put("remoteDhPublic", state.remoteDhPublicKey?.let { Base64.getEncoder().encodeToString(it.encoded) })
        put("sendCounter", state.sendCounter)
        put("receiveCounter", state.receiveCounter)
        put("previousChainLength", state.previousChainLength)
      }
      val sanitizedId = contactId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
      val targetFile = File(baseDir, "ratchet_$sanitizedId.json")
      targetFile.writeText(json.toString())
    }
  }

  override fun loadSession(contactId: String): RatchetSessionState? {
    return runCatching {
      val sanitizedId = contactId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
      val targetFile = File(baseDir, "ratchet_$sanitizedId.json")
      if (!targetFile.exists()) return null

      val json = JSONObject(targetFile.readText())
      val rootKey = Base64.getDecoder().decode(json.getString("rootKey"))
      val sendingChainKey = if (json.has("sendingChainKey") && !json.isNull("sendingChainKey")) {
        Base64.getDecoder().decode(json.getString("sendingChainKey"))
      } else null
      val receivingChainKey = if (json.has("receivingChainKey") && !json.isNull("receivingChainKey")) {
        Base64.getDecoder().decode(json.getString("receivingChainKey"))
      } else null

      val kf = KeyFactory.getInstance("EC")
      val localPub = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(json.getString("localDhPublic"))))
      val localPriv = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(json.getString("localDhPrivate"))))
      val localKeyPair = KeyPair(localPub, localPriv)

      val remoteDhPublic = if (json.has("remoteDhPublic") && !json.isNull("remoteDhPublic")) {
        kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(json.getString("remoteDhPublic"))))
      } else null

      RatchetSessionState(
        contactId = json.getString("contactId"),
        rootKey = rootKey,
        sendingChainKey = sendingChainKey,
        receivingChainKey = receivingChainKey,
        localDhKeyPair = localKeyPair,
        remoteDhPublicKey = remoteDhPublic,
        sendCounter = json.getInt("sendCounter"),
        receiveCounter = json.getInt("receiveCounter"),
        previousChainLength = json.getInt("previousChainLength"),
      )
    }.getOrNull()
  }

  override fun removeSession(contactId: String) {
    runCatching {
      val sanitizedId = contactId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
      val targetFile = File(baseDir, "ratchet_$sanitizedId.json")
      if (targetFile.exists()) {
        targetFile.delete()
      }
    }
  }
}

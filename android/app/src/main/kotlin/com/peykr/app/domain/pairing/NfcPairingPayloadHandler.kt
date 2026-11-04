package com.peykr.app.domain.pairing

import java.nio.charset.StandardCharsets

object NfcPairingPayloadHandler {
  const val MIME_TYPE = "application/vnd.com.peykr.pairing"

  fun createNdefPayload(pairingQrPayload: PairingQrPayload): ByteArray {
    val serialized = pairingQrPayload.serialize()
    return serialized.toByteArray(StandardCharsets.UTF_8)
  }

  fun parseNdefPayload(bytes: ByteArray): Result<PairingQrPayload> {
    return runCatching {
      val text = String(bytes, StandardCharsets.UTF_8)
      PairingQrPayload.parse(text).getOrThrow()
    }
  }
}

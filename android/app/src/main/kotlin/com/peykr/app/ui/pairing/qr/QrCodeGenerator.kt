package com.peykr.app.ui.pairing.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.peykr.app.domain.pairing.PairingException
import com.peykr.app.domain.pairing.PairingQrPayload

data class QrMatrix(
  val width: Int,
  val height: Int,
  val bits: BooleanArray,
) {
  fun get(x: Int, y: Int): Boolean = bits[y * width + x]

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is QrMatrix) return false
    return width == other.width && height == other.height && bits.contentEquals(other.bits)
  }

  override fun hashCode(): Int {
    var result = width
    result = 31 * result + height
    result = 31 * result + bits.contentHashCode()
    return result
  }
}

object QrCodeGenerator {

  private const val DEFAULT_MODULE_SIZE = 256
  private const val QUIET_ZONE_MODULES = 2

  fun generateQrMatrix(
    content: String,
    size: Int = DEFAULT_MODULE_SIZE,
  ): Result<QrMatrix> = runCatching {
    if (content.length > PairingQrPayload.MAX_QR_PAYLOAD_LENGTH) {
      throw PairingException.OversizedPayload(
        "Payload length (${content.length}) exceeds maximum allowable QR size (${PairingQrPayload.MAX_QR_PAYLOAD_LENGTH})"
      )
    }

    val hints = mapOf(
      EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
      EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
      EncodeHintType.CHARACTER_SET to "UTF-8",
    )

    val bitMatrix = QRCodeWriter().encode(
      content,
      BarcodeFormat.QR_CODE,
      size,
      size,
      hints,
    )

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bits = BooleanArray(width * height)

    for (y in 0 until height) {
      val offset = y * width
      for (x in 0 until width) {
        bits[offset + x] = bitMatrix.get(x, y)
      }
    }

    QrMatrix(width, height, bits)
  }

  fun generateQrBitmap(
    content: String,
    sizePx: Int = DEFAULT_MODULE_SIZE,
    foregroundColor: Int = Color.BLACK,
    backgroundColor: Int = Color.WHITE,
    bitmapFactory: (width: Int, height: Int, pixels: IntArray) -> Bitmap = { w, h, px ->
      val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      bm.setPixels(px, 0, w, 0, 0, w, h)
      bm
    },
  ): Result<Bitmap> = runCatching {
    val matrix = generateQrMatrix(content, sizePx).getOrThrow()
    val pixels = IntArray(matrix.width * matrix.height)

    for (i in pixels.indices) {
      pixels[i] = if (matrix.bits[i]) foregroundColor else backgroundColor
    }

    bitmapFactory(matrix.width, matrix.height, pixels)
  }
}

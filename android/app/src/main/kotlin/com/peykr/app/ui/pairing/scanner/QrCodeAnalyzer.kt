package com.peykr.app.ui.pairing.scanner

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.peykr.app.domain.pairing.PairingQrPayload
import java.util.concurrent.atomic.AtomicBoolean

class QrCodeAnalyzer(
  private val onQrCodeDetected: (String) -> Unit,
  scannerProvider: (() -> BarcodeScanner)? = null,
) : ImageAnalysis.Analyzer, java.io.Closeable {

  private val scanner: BarcodeScanner? by lazy {
    try {
      scannerProvider?.invoke() ?: BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
          .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
          .build()
      )
    } catch (_: Throwable) {
      null
    }
  }

  private val isProcessing = AtomicBoolean(false)
  @Volatile private var lastScannedPayload: String? = null
  @Volatile private var lastScannedTimestamp: Long = 0L

  @androidx.annotation.OptIn(ExperimentalGetImage::class)
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    val activeScanner = scanner
    if (mediaImage == null || activeScanner == null || !isProcessing.compareAndSet(false, true)) {
      imageProxy.close()
      return
    }

    try {
      val rotationDegrees = imageProxy.imageInfo.rotationDegrees
      val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

      activeScanner.process(image)
        .addOnSuccessListener { barcodes ->
          for (barcode in barcodes) {
            if (barcode.format != Barcode.FORMAT_QR_CODE) continue
            val rawValue = barcode.rawValue ?: continue

            if (rawValue.length > PairingQrPayload.MAX_QR_PAYLOAD_LENGTH) {
              continue
            }

            val now = System.currentTimeMillis()
            if (rawValue != lastScannedPayload || (now - lastScannedTimestamp) > DEBOUNCE_MILLIS) {
              lastScannedPayload = rawValue
              lastScannedTimestamp = now
              onQrCodeDetected(rawValue)
              break
            }
          }
        }
        .addOnCompleteListener {
          imageProxy.close()
          isProcessing.set(false)
        }
    } catch (e: Throwable) {
      imageProxy.close()
      isProcessing.set(false)
    }
  }

  override fun close() {
    try {
      scanner?.close()
    } catch (_: Exception) {}
  }

  fun resetScanMemory() {
    lastScannedPayload = null
    lastScannedTimestamp = 0L
  }

  companion object {
    private const val DEBOUNCE_MILLIS = 2000L
  }
}

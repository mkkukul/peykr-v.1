package com.peykr.app.ui.pairing.scanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.peykr.app.R
import com.peykr.app.ui.theme.Navy950
import com.peykr.app.ui.theme.PrimaryTurquoise
import com.peykr.app.ui.theme.TextPrimary
import com.peykr.app.ui.theme.TextSecondary
import java.util.concurrent.Executors

@Composable
fun CameraScannerView(
  modifier: Modifier = Modifier,
  onQrCodeScanned: (String) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  // Refresh permission when resuming from app settings or background
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  var cameraBindingError by remember { mutableStateOf<String?>(null) }
  var retryKey by remember { mutableIntStateOf(0) }

  val registryOwner = LocalActivityResultRegistryOwner.current
  val permissionLauncher = if (registryOwner != null) {
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      hasCameraPermission = granted
    }
  } else null

  LaunchedEffect(hasCameraPermission, registryOwner) {
    if (!hasCameraPermission && permissionLauncher != null) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Box(modifier = modifier.fillMaxSize().background(Navy950), contentAlignment = Alignment.Center) {
    if (hasCameraPermission) {
      if (cameraBindingError != null) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = stringResource(R.string.camera_init_failed_title),
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.camera_init_failed_desc),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(16.dp))
          Button(
            onClick = {
              cameraBindingError = null
              retryKey++
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
          ) {
            Text(stringResource(R.string.camera_init_retry), color = Navy950)
          }
        }
      } else {
        val cameraExecutor = remember(retryKey) { Executors.newSingleThreadExecutor() }
        val analyzer = remember(retryKey) { QrCodeAnalyzer(onQrCodeScanned) }
        var activeCameraProvider by remember(retryKey) { mutableStateOf<ProcessCameraProvider?>(null) }

        AndroidView(
          factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
              scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
              try {
                val cameraProvider = cameraProviderFuture.get()
                activeCameraProvider = cameraProvider

                val preview = Preview.Builder().build().also {
                  it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
                  .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                  }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  cameraSelector,
                  preview,
                  imageAnalysis,
                )
              } catch (e: Exception) {
                cameraBindingError = e.message ?: "Camera binding failed"
              }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(16.dp)),
        )

        DisposableEffect(retryKey) {
          onDispose {
            try {
              activeCameraProvider?.unbindAll()
            } catch (_: Exception) {}
            analyzer.close()
            cameraExecutor.shutdown()
          }
        }
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(R.string.camera_permission_required_title),
          color = TextPrimary,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.camera_permission_required_desc),
          color = TextSecondary,
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))

        Button(
          onClick = { permissionLauncher?.launch(Manifest.permission.CAMERA) },
          enabled = permissionLauncher != null,
          colors = ButtonDefaults.buttonColors(containerColor = PrimaryTurquoise),
        ) {
          Text(stringResource(R.string.camera_permission_grant), color = Navy950)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
          onClick = { openAppSettings(context) },
        ) {
          Text(stringResource(R.string.camera_permission_settings), color = PrimaryTurquoise)
        }
      }
    }
  }
}

private fun openAppSettings(context: Context) {
  val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", context.packageName, null)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
  }
  context.startActivity(intent)
}

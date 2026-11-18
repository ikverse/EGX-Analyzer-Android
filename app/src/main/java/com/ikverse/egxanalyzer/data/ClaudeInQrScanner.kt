package com.ikverse.egxanalyzer.data

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * The camera, reading ClaudeIn's pairing QR code.
 *
 * CameraX for the picture and ML Kit's **bundled** barcode model for the reading: the model ships
 * inside the app, so it works on a device without Google Play services (the Huawei tablet), where
 * Google's ready-made scanner screen would not open at all. QR codes only, nothing else decoded.
 *
 * The camera permission is asked for here, the first time this is drawn - which is only ever after
 * the owner pressed Scan - and never at launch, so a phone paired by typing is never asked.
 * Frames are read on the phone and dropped; nothing is stored or sent anywhere.
 */
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ClaudeInQrScanner(modifier: Modifier, onCode: (String) -> Unit, onDenied: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode by rememberUpdatedState(onCode)
    val latestOnDenied by rememberUpdatedState(onDenied)
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (!ok) latestOnDenied()
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Box(modifier)
        return
    }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var delivered = false
        providerFuture.addListener({
            val cameras = providerFuture.get()
            provider = cameras
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { frame ->
                val media = frame.image
                if (media == null || delivered) {
                    frame.close()
                    return@setAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(media, frame.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        val text = codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                        if (text != null && !delivered) {
                            delivered = true
                            ContextCompat.getMainExecutor(context).execute { latestOnCode(text) }
                        }
                    }
                    .addOnCompleteListener { frame.close() }
            }
            runCatching {
                cameras.unbindAll()
                cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

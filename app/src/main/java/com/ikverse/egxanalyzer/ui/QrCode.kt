package com.ikverse.egxanalyzer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The sign-in link as a code another device can scan.
 *
 * Drawn light-on-dark would be unscannable by most readers, so the code keeps its white quiet zone
 * whatever the app's theme: a QR that matches the surroundings is a decoration, not a login.
 */
@Composable
internal fun QrCode(content: String, modifier: Modifier = Modifier, size: Dp = QrSize) {
    val bitmap = remember(content) { encode(content) } ?: return
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
}

private fun encode(content: String): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        QR_PIXELS,
        QR_PIXELS,
        mapOf(
            // A login link is short, so the highest correction costs little and survives a screen
            // photographed at an angle.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2,
        ),
    )
    val dark = Color.Black.toArgb()
    val light = Color.White.toArgb()
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) dark else light
    }
    android.graphics.Bitmap.createBitmap(
        pixels,
        matrix.width,
        matrix.height,
        android.graphics.Bitmap.Config.ARGB_8888,
    ).asImageBitmap()
}.getOrNull()

private const val QR_PIXELS = 512
private val QrSize = 240.dp

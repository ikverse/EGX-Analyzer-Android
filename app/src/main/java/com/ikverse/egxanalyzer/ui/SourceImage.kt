package com.ikverse.egxanalyzer.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decodes a stored source image, downscaled, off the main thread.
 *
 * Returns null rather than throwing when the file has gone: these paths point into Telegram's own
 * storage, which it prunes on its own schedule, so a saved analysis can outlive its images.
 */
@Composable
private fun rememberSourceImage(path: String?, maxPixels: Int): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path, maxPixels) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(Uri.parse(path ?: return@runCatching null).path ?: return@runCatching null)
                if (!file.isFile) return@runCatching null
                // Measure first so a full-size chart is never decoded just to draw a thumbnail.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPixels) sample *= 2
                BitmapFactory
                    .decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

/** Small preview of a cited source image; falls back to its reference when unavailable. */
@Composable
internal fun SourceImageThumbnail(
    path: String?,
    reference: Int?,
    size: Dp = 40.dp,
    onOpen: () -> Unit,
) {
    val bitmap = rememberSourceImage(path, maxPixels = 256)
    if (bitmap == null) {
        Text(
            reference?.let { "#$it" } ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Image(
        bitmap = bitmap,
        contentDescription = reference?.let { "Source image $it" } ?: "Source image",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onOpen),
    )
}

/** Full-screen view of one source image, for checking a number against the original. */
@Composable
internal fun SourceImageViewer(path: String?, reference: Int?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val bitmap = rememberSourceImage(path, maxPixels = 2048)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = reference?.let { "Source image $it" } ?: "Source image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "This image is no longer stored on the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(28.dp),
                )
            }
        }
    }
}

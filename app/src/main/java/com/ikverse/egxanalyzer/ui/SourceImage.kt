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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogProperties
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

/**
 * Full-screen view of one source image, zoomable.
 *
 * These are dense Arabic price cards photographed at whatever resolution the channel posted, so
 * reading a number off one at fit-to-width is often impossible. Pinch to zoom, drag to pan,
 * double-tap to toggle - and panning is bounded so the image cannot be flung out of sight.
 */
@Composable
internal fun SourceImageViewer(path: String?, reference: Int?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bitmap = rememberSourceImage(path, maxPixels = 4096)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val viewport = with(LocalDensity.current) {
                        Size(maxWidth.toPx(), maxHeight.toPx())
                    }

                    /** Keeps the image inside the viewport whatever the zoom. */
                    fun clamp(candidate: Offset, atScale: Float): Offset {
                        val slackX = (viewport.width * (atScale - 1f) / 2f).coerceAtLeast(0f)
                        val slackY = (viewport.height * (atScale - 1f) / 2f).coerceAtLeast(0f)
                        return Offset(
                            candidate.x.coerceIn(-slackX, slackX),
                            candidate.y.coerceIn(-slackY, slackY),
                        )
                    }

                    Image(
                        bitmap = bitmap,
                        contentDescription = reference?.let { "Source image $it" } ?: "Source image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    // A tap outside any zoom is still the way out.
                                    onTap = { if (scale <= 1f) onDismiss() },
                                    onDoubleTap = { tap ->
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = DoubleTapScale
                                            val centre = Offset(viewport.width / 2f, viewport.height / 2f)
                                            offset = clamp(
                                                (centre - tap) * (DoubleTapScale - 1f),
                                                DoubleTapScale,
                                            )
                                        }
                                    },
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val next = (scale * zoom).coerceIn(1f, MaxScale)
                                    scale = next
                                    offset = if (next <= 1f) {
                                        Offset.Zero
                                    } else {
                                        clamp(offset + pan, next)
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                }
            } else {
                Text(
                    "This image is no longer stored on the device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(28.dp),
                )
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Text("Close", color = Color.White)
            }
        }
    }
}

private const val MaxScale = 6f
private const val DoubleTapScale = 2.5f

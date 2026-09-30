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
 * What has already been decoded, so a picture shown once does not flash back to its placeholder on
 * every tab switch or fold.
 *
 * A chat's photo used to be redecoded from disk on every composition that asked for it - which is
 * cheap once, but this app disposes whole subtrees on the ordinary things a reader does: switching
 * tabs, and folding or unfolding the phone (see `LiveAppState`'s own note on why every `remember` in
 * every screen dies with the shell that composed it). Each of those redraws was the letter-placeholder
 * flashing before the real picture reappeared. Held here rather than on a `remember`, because the
 * whole point is to survive exactly the disposals a `remember` does not.
 *
 * Bounded by decoded bytes rather than by entry count: a 256px avatar and a 4096px screenshot are not
 * the same cost, and a count-based limit either wastes the budget on avatars or starves them for one
 * large image. 16 MB is dozens of avatars and thumbnails, or a couple of full-size screenshots, and an
 * entry too big to fit is simply not cached rather than evicting everything ahead of it.
 */
private object TelegramImageCache {
    private val bytes = object : android.util.LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(path: String, maxPixels: Int): ImageBitmap? = bytes.get("$path@$maxPixels")

    fun put(path: String, maxPixels: Int, bitmap: ImageBitmap) {
        bytes.put("$path@$maxPixels", bitmap)
    }
}

/**
 * Decodes an image out of Telegram's storage, downscaled, off the main thread.
 *
 * Returns null rather than throwing when the file has gone: these paths point into Telegram's own
 * storage, which it prunes on its own schedule, so a saved analysis can outlive its images and a
 * chat can outlive its picture. Shared with the chat list, whose profile photos come out of the
 * same cache and would otherwise want a second decoder saying the same thing.
 *
 * Checks [TelegramImageCache] first, so a picture already decoded once - by this card or any other
 * asking about the same path at the same size - returns immediately instead of hitting the disk
 * again.
 */
@Composable
internal fun rememberTelegramImage(path: String?, maxPixels: Int): ImageBitmap? {
    val cached = path?.let { TelegramImageCache.get(it, maxPixels) }
    val bitmap by produceState(cached, path, maxPixels) {
        if (cached != null) return@produceState
        val safePath = path
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(Uri.parse(safePath ?: return@runCatching null).path ?: return@runCatching null)
                if (!file.isFile) return@runCatching null
                // Measure first so a full-size chart is never decoded just to draw a thumbnail.
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxPixels) sample *= 2
                BitmapFactory
                    .decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }.getOrNull()?.also { if (safePath != null) TelegramImageCache.put(safePath, maxPixels, it) }
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
    val bitmap = rememberTelegramImage(path, maxPixels = 256)
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
        val bitmap = rememberTelegramImage(path, maxPixels = 4096)
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

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(Space.m)) {
                Text("Close", color = Color.White)
            }
        }
    }
}

private const val MaxScale = 6f
private const val DoubleTapScale = 2.5f

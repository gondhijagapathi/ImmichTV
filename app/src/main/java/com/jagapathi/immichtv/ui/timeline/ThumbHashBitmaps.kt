package com.jagapathi.immichtv.ui.timeline

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import com.jagapathi.immichtv.util.ThumbHash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Base64
import kotlin.math.roundToInt

// Placeholders are tiny (about 4 KB), so keeping a few hundred covers several screens of tiles.
private val placeholderCache = LruCache<String, Bitmap>(512)

/** The placeholder for a base64 ThumbHash, or null if there's none or it can't be decoded. */
internal fun thumbHashBitmap(hash: String?): Bitmap? {
    if (hash.isNullOrEmpty()) return null
    placeholderCache.get(hash)?.let { return it }
    return try {
        val image = ThumbHash.decode(Base64.getDecoder().decode(hash))
        Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888)
            .also { placeholderCache.put(hash, it) }
    } catch (e: Exception) {
        // A malformed hash only costs the placeholder.
        Log.w("ThumbHash", "Couldn't decode thumbhash", e)
        null
    }
}

/**
 * The placeholder for a base64 ThumbHash, decoded in the background. Decoding one takes around
 * half a millisecond, so doing it on the main thread for each of the seven tiles in a row that
 * scrolls in costs frames. Draws nothing until it's ready, which is usually before its tile is on
 * screen since the grid composes tiles a row ahead.
 */
@Composable
internal fun rememberThumbHashPainter(hash: String?): Painter? {
    if (hash.isNullOrEmpty()) return null
    return remember(hash) { ThumbHashPlaceholder(hash) }.painter
}

/**
 * Starts decoding once its tile is in the composition. The decode isn't cancelled if the tile
 * scrolls away before it finishes: it's quick, and its result is cached for when the tile comes back.
 */
private class ThumbHashPlaceholder(private val hash: String) : RememberObserver {
    val painter = ThumbHashPainter(placeholderCache.get(hash)?.asImageBitmap())

    override fun onRemembered() {
        if (painter.image != null) return
        decodeScope.launch { painter.image = thumbHashBitmap(hash)?.asImageBitmap() }
    }

    override fun onForgotten() = Unit

    override fun onAbandoned() = Unit
}

private val decodeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Draws [image] like a [androidx.compose.ui.graphics.painter.BitmapPainter], or nothing while it's null. */
private class ThumbHashPainter(cached: ImageBitmap?) : Painter() {
    var image by mutableStateOf(cached)

    private var alpha = 1f
    private var colorFilter: ColorFilter? = null

    override val intrinsicSize: Size
        get() = image?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Unspecified

    override fun DrawScope.onDraw() {
        val image = image ?: return
        drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = alpha, colorFilter = colorFilter)
    }

    // Applied while drawing, so fading into the thumbnail doesn't need an offscreen layer.
    override fun applyAlpha(alpha: Float): Boolean {
        this.alpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        this.colorFilter = colorFilter
        return true
    }
}

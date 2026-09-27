package com.jagapathi.immichtv.ui.timeline

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.jagapathi.immichtv.util.ThumbHash
import java.util.Base64

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

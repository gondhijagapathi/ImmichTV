package com.jagapathi.immichtv.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A decoded placeholder: [width] x [height] pixels, row by row, as ARGB ints. */
class ThumbHashImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Decodes [ThumbHash](https://evanw.github.io/thumbhash/) placeholders, the blurry previews Immich
 * sends with every asset so a grid can show something before the thumbnails arrive. Ported from
 * the reference Java implementation by Evan Wallace (MIT licensed).
 */
object ThumbHash {

    /** Decodes a hash into an image at most 32px on its longer side. */
    fun decode(hash: ByteArray): ThumbHashImage {
        val header24 = (hash[0].toInt() and 255) or
            ((hash[1].toInt() and 255) shl 8) or
            ((hash[2].toInt() and 255) shl 16)
        val header16 = (hash[3].toInt() and 255) or ((hash[4].toInt() and 255) shl 8)
        val lDc = (header24 and 63) / 63f
        val pDc = ((header24 shr 6) and 63) / 31.5f - 1f
        val qDc = ((header24 shr 12) and 63) / 31.5f - 1f
        val lScale = ((header24 shr 18) and 31) / 31f
        val hasAlpha = (header24 shr 23) != 0
        val pScale = ((header16 shr 3) and 63) / 63f
        val qScale = ((header16 shr 9) and 63) / 63f
        val isLandscape = (header16 shr 15) != 0
        val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else header16 and 7)
        val ly = max(3, if (isLandscape) header16 and 7 else if (hasAlpha) 5 else 7)
        val aDc = if (hasAlpha) (hash[5].toInt() and 15) / 15f else 1f
        val aScale = if (hasAlpha) ((hash[5].toInt() shr 4) and 15) / 15f else 0f

        // Read the varying factors, boosting saturation by 1.25x to make up for quantization.
        val acStart = if (hasAlpha) 6 else 5
        var acIndex = 0
        val lAc = Channel(lx, ly).also { acIndex = it.decode(hash, acStart, acIndex, lScale) }.ac
        val pAc = Channel(3, 3).also { acIndex = it.decode(hash, acStart, acIndex, pScale * 1.25f) }.ac
        val qAc = Channel(3, 3).also { acIndex = it.decode(hash, acStart, acIndex, qScale * 1.25f) }.ac
        val aAc = if (hasAlpha) Channel(5, 5).also { it.decode(hash, acStart, acIndex, aScale) }.ac else null

        val ratio = approximateAspectRatio(hash)
        val w = (if (ratio > 1f) 32f else 32f * ratio).roundToInt()
        val h = (if (ratio > 1f) 32f / ratio else 32f).roundToInt()
        val argb = IntArray(w * h)
        val fx = FloatArray(max(lx, if (hasAlpha) 5 else 3))
        val fy = FloatArray(max(ly, if (hasAlpha) 5 else 3))

        for (y in 0 until h) {
            for (x in 0 until w) {
                var l = lDc
                var p = pDc
                var q = qDc
                var a = aDc

                for (cx in fx.indices) fx[cx] = cos(PI / w * (x + 0.5f) * cx).toFloat()
                for (cy in fy.indices) fy[cy] = cos(PI / h * (y + 0.5f) * cy).toFloat()

                var j = 0
                for (cy in 0 until ly) {
                    val fy2 = fy[cy] * 2f
                    var cx = if (cy > 0) 0 else 1
                    while (cx * ly < lx * (ly - cy)) {
                        l += lAc[j++] * fx[cx] * fy2
                        cx++
                    }
                }

                j = 0
                for (cy in 0 until 3) {
                    val fy2 = fy[cy] * 2f
                    for (cx in (if (cy > 0) 0 else 1) until 3 - cy) {
                        val f = fx[cx] * fy2
                        p += pAc[j] * f
                        q += qAc[j] * f
                        j++
                    }
                }

                if (aAc != null) {
                    j = 0
                    for (cy in 0 until 5) {
                        val fy2 = fy[cy] * 2f
                        for (cx in (if (cy > 0) 0 else 1) until 5 - cy) {
                            a += aAc[j++] * fx[cx] * fy2
                        }
                    }
                }

                val b = l - 2f / 3f * p
                val r = (3f * l - b + q) / 2f
                val g = r - q
                argb[y * w + x] = (a.toColorByte() shl 24) or
                    (r.toColorByte() shl 16) or
                    (g.toColorByte() shl 8) or
                    b.toColorByte()
            }
        }
        return ThumbHashImage(w, h, argb)
    }

    /** The approximate width / height of the original image. */
    fun approximateAspectRatio(hash: ByteArray): Float {
        val header = hash[3].toInt()
        val hasAlpha = (hash[2].toInt() and 0x80) != 0
        val isLandscape = (hash[4].toInt() and 0x80) != 0
        val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else header and 7
        val ly = if (isLandscape) header and 7 else if (hasAlpha) 5 else 7
        return lx.toFloat() / ly.toFloat()
    }

    private fun Float.toColorByte() = max(0, (255f * min(1f, this)).roundToInt())

    private class Channel(nx: Int, ny: Int) {
        val ac: FloatArray

        init {
            var n = 0
            for (cy in 0 until ny) {
                var cx = if (cy > 0) 0 else 1
                while (cx * ny < nx * (ny - cy)) {
                    n++
                    cx++
                }
            }
            ac = FloatArray(n)
        }

        fun decode(hash: ByteArray, start: Int, startIndex: Int, scale: Float): Int {
            var index = startIndex
            for (i in ac.indices) {
                val data = hash[start + (index shr 1)].toInt() shr ((index and 1) shl 2)
                ac[i] = ((data and 15) / 7.5f - 1f) * scale
                index++
            }
            return index
        }
    }
}

package com.jagapathi.immichtv.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

/**
 * Expected values come from running the reference Java implementation
 * (github.com/evanw/thumbhash) on the same hashes.
 */
class ThumbHashTest {

    private fun decode(base64: String) = ThumbHash.decode(Base64.getDecoder().decode(base64))

    /** The pixel at [index] as (r, g, b, a), to compare with the reference's RGBA output. */
    private fun ThumbHashImage.rgbaAt(index: Int): List<Int> {
        val pixel = argb[index]
        return listOf((pixel shr 16) and 255, (pixel shr 8) and 255, pixel and 255, (pixel ushr 24) and 255)
    }

    private fun ThumbHashImage.channelSum(): Long = argb.sumOf { pixel ->
        ((pixel shr 16) and 255) + ((pixel shr 8) and 255) + (pixel and 255) + ((pixel ushr 24) and 255).toLong()
    }

    @Test
    fun `decodes a landscape hash like the reference`() {
        val image = decode("3wcKPZqAh4dweIh4iHiIiIBwCPh4")

        assertEquals(32, image.width)
        assertEquals(23, image.height)
        assertEquals(listOf(36, 24, 171, 255), image.rgbaAt(0))
        assertEquals(listOf(127, 116, 131, 255), image.rgbaAt(368))
        assertEquals(listOf(255, 245, 109, 255), image.rgbaAt(735))
        assertEquals(listOf(255, 22, 140, 255), image.rgbaAt(31))
        assertEquals(463875L, image.channelSum())
    }

    @Test
    fun `decodes a portrait hash like the reference`() {
        val image = decode("3/cJPRqAh4CIiId4h3h4iICACPh3")

        assertEquals(23, image.width)
        assertEquals(32, image.height)
        assertEquals(listOf(27, 39, 164, 255), image.rgbaAt(0))
        assertEquals(listOf(255, 9, 142, 255), image.rgbaAt(22))
        assertEquals(464145L, image.channelSum())
    }

    @Test
    fun `decodes a hash with transparency like the reference`() {
        val image = decode("2naFHQ5YRoUwmIh4h0YwaPR8j4V4eIiIiA==")

        assertEquals(32, image.width)
        assertEquals(32, image.height)
        assertEquals(listOf(11, 128, 126, 253), image.rgbaAt(512))
        assertEquals(listOf(48, 168, 138, 22), image.rgbaAt(1023))
        assertEquals(459286L, image.channelSum())
    }

    @Test
    fun `reports the approximate aspect ratio`() {
        assertEquals(7f / 5f, ThumbHash.approximateAspectRatio(Base64.getDecoder().decode("3wcKPZqAh4dweIh4iHiIiIBwCPh4")))
    }
}

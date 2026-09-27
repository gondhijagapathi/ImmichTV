package com.jagapathi.immichtv.ui.timeline

import com.jagapathi.immichtv.model.TimeBucketAssetsDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TimelineAssetTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maps columns to assets and applies the local offset`() {
        val dto = json.decodeFromString<TimeBucketAssetsDto>(
            """
            {
              "id": ["a1", "a2"],
              "ownerId": ["u1", "u1"],
              "ratio": [1.333, 0.75],
              "isFavorite": [true, false],
              "visibility": ["timeline", "timeline"],
              "isTrashed": [false, false],
              "isImage": [true, false],
              "thumbhash": ["3wcKPZqAh4dweIh4iHiIiIBwCPh4", null],
              "fileCreatedAt": ["2024-05-01T20:00:00.123", "2024-05-01T02:30:00Z"],
              "localOffsetHours": [5.5, -7],
              "duration": [null, 15250],
              "projectionType": [null, null],
              "livePhotoVideoId": [null, null],
              "city": ["Hyderabad", null],
              "country": ["India", null],
              "stack": [null, null]
            }
            """
        )

        val assets = dto.toTimelineAssets()

        assertEquals(listOf("a1", "a2"), assets.map { it.id })
        // 20:00 UTC in India is 01:30 the next day.
        assertEquals(LocalDateTime.of(2024, 5, 2, 1, 30, 0, 123_000_000), assets[0].takenAt)
        assertEquals(LocalDateTime.of(2024, 4, 30, 19, 30), assets[1].takenAt)
        assertEquals(true, assets[0].isFavorite)
        assertEquals(false, assets[1].isImage)
        assertNull(assets[0].duration)
        assertEquals(15_250.milliseconds, assets[1].duration)
        assertEquals("3wcKPZqAh4dweIh4iHiIiIBwCPh4", assets[0].thumbhash)
        assertEquals("Hyderabad", assets[0].city)
        assertNull(assets[1].country)
    }

    @Test
    fun `tolerates missing optional columns and skips assets without a date`() {
        val dto = json.decodeFromString<TimeBucketAssetsDto>(
            """{"id": ["a1", "a2"], "fileCreatedAt": ["not a date", "2024-05-01T10:00:00"], "isImage": [true, true]}"""
        )

        val assets = dto.toTimelineAssets()

        assertEquals(listOf("a2"), assets.map { it.id })
        assertEquals(LocalDateTime.of(2024, 5, 1, 10, 0), assets[0].takenAt)
        assertNull(assets[0].city)
    }

    @Test
    fun `reads durations from both old and new servers`() {
        assertEquals(15.seconds + 123.milliseconds, parseDuration(JsonPrimitive("0:00:15.123000")))
        assertEquals(3_723.seconds, parseDuration(JsonPrimitive("01:02:03")))
        assertEquals(15_000.milliseconds, parseDuration(JsonPrimitive(15000)))
        // Older servers send a zero duration for photos.
        assertNull(parseDuration(JsonPrimitive("0:00:00.00000")))
        assertNull(parseDuration(JsonNull))
        assertNull(parseDuration(null))
        assertNull(parseDuration(JsonPrimitive("soon")))
    }

    @Test
    fun `reads timestamps with and without an offset as UTC`() {
        val expected = LocalDateTime.of(2024, 5, 1, 10, 0)
        assertEquals(expected, parseUtcDateTime("2024-05-01T10:00:00"))
        assertEquals(expected, parseUtcDateTime("2024-05-01T10:00:00.000Z"))
        assertEquals(expected, parseUtcDateTime("2024-05-01T12:00:00+02:00"))
        assertNull(parseUtcDateTime("yesterday"))
    }

    @Test
    fun `formats durations like a video player`() {
        assertEquals("0:07", formatDuration(7.seconds))
        assertEquals("12:34", formatDuration(754.seconds))
        assertEquals("1:02:03", formatDuration(3_723.seconds))
    }
}

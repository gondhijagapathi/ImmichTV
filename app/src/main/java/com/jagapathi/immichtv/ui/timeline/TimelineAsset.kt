package com.jagapathi.immichtv.ui.timeline

import com.jagapathi.immichtv.model.TimeBucketAssetsDto
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A photo or video on a timeline. */
data class TimelineAsset(
    val id: String,
    /** When it was taken, in the time zone it was taken in. Used for day headers. */
    val takenAt: LocalDateTime,
    val isImage: Boolean,
    val isFavorite: Boolean,
    /** How long a video runs, or null for photos. */
    val duration: Duration?,
    /** Base64 [ThumbHash][com.jagapathi.immichtv.util.ThumbHash] placeholder, if the server made one. */
    val thumbhash: String?,
    val city: String?,
    val country: String?
)

/** Turns Immich's column-by-column response into one [TimelineAsset] per asset. */
fun TimeBucketAssetsDto.toTimelineAssets(): List<TimelineAsset> = id.indices.mapNotNull { i ->
    // An asset without a usable date can't be placed on the timeline.
    val utc = fileCreatedAt.getOrNull(i)?.let(::parseUtcDateTime) ?: return@mapNotNull null
    val offsetSeconds = ((localOffsetHours.getOrNull(i) ?: 0.0) * 3600).roundToLong()
    TimelineAsset(
        id = id[i],
        takenAt = utc.plusSeconds(offsetSeconds),
        isImage = isImage.getOrElse(i) { true },
        isFavorite = isFavorite.getOrElse(i) { false },
        duration = parseDuration(duration.getOrNull(i)),
        thumbhash = thumbhash.getOrNull(i),
        city = city.getOrNull(i),
        country = country.getOrNull(i)
    )
}

/** Reads a UTC timestamp that may or may not carry an offset (`...10:00:00` or `...10:00:00Z`). */
internal fun parseUtcDateTime(value: String): LocalDateTime? = try {
    when (val parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(value, OffsetDateTime::from, LocalDateTime::from)) {
        is OffsetDateTime -> parsed.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime()
        is LocalDateTime -> parsed
        else -> null
    }
} catch (e: DateTimeParseException) {
    null
}

/** Reads a duration sent as milliseconds (Immich v3+) or as `H:MM:SS.ffffff` (older servers). */
internal fun parseDuration(value: JsonPrimitive?): Duration? {
    if (value == null || value is JsonNull) return null
    val millis = if (value.isString) parseClockMillis(value.content) else value.doubleOrNull?.roundToLong()
    return millis?.takeIf { it > 0 }?.milliseconds
}

private fun parseClockMillis(text: String): Long? {
    val parts = text.split(':')
    if (parts.size != 3) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toDoubleOrNull() ?: return null
    return (hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).roundToLong()
}

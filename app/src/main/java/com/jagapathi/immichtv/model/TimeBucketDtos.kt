package com.jagapathi.immichtv.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** One month of a timeline as returned by `GET /api/timeline/buckets`. */
@Serializable
data class TimeBucketDto(
    /** The first day of the month, e.g. `2024-05-01`. */
    val timeBucket: String,
    val count: Int
)

/**
 * The assets of one month as returned by `GET /api/timeline/bucket`. Immich sends them column by
 * column: the n-th entry of every list belongs to the n-th asset. Only the columns the app uses
 * are declared.
 */
@Serializable
data class TimeBucketAssetsDto(
    val id: List<String> = emptyList(),
    /** UTC, without an offset on most servers, e.g. `2024-05-01T10:00:00.123`. */
    val fileCreatedAt: List<String> = emptyList(),
    /** Hours to add to [fileCreatedAt] to get the local time the photo was taken. */
    val localOffsetHours: List<Double?> = emptyList(),
    val isImage: List<Boolean> = emptyList(),
    val isFavorite: List<Boolean> = emptyList(),
    /** Milliseconds on Immich v3 and later, an `H:MM:SS.ffffff` string before that. */
    val duration: List<JsonPrimitive?> = emptyList(),
    val thumbhash: List<String?> = emptyList(),
    val city: List<String?> = emptyList(),
    val country: List<String?> = emptyList()
)

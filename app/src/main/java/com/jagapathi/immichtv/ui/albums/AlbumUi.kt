package com.jagapathi.immichtv.ui.albums

import com.jagapathi.immichtv.model.ImmichAlbumDto
import com.jagapathi.immichtv.model.TimelineQuery
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** An album as the Albums tab and the album page show it. */
data class AlbumUi(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String?,
    val assetCount: Int,
    /** When the oldest photo was taken, in the time zone it was taken in. Null for an empty album. */
    val oldestPhotoAt: LocalDateTime?,
    /** When the newest photo was taken, in the time zone it was taken in. Null for an empty album. */
    val newestPhotoAt: LocalDateTime?,
    val isShared: Boolean,
    /** Who shared the album with this user, or null if it's their own. */
    val sharedBy: String?,
    /** The album's photos, in the order chosen for it in Immich. */
    val timeline: TimelineQuery
)

/** The albums whose newest photo is from [year], or the empty albums when [year] is null. */
data class AlbumGroup(val year: Int?, val albums: List<AlbumUi>)

/** [userId] is the signed-in user, to tell their own albums from ones shared with them. */
internal fun ImmichAlbumDto.toAlbumUi(userId: String, thumbnailUrl: (assetId: String) -> String): AlbumUi {
    val owner = albumOwner
    val sharedBy = if (owner == null || owner.id == userId) null else owner.name?.ifBlank { null } ?: owner.email
    return AlbumUi(
        id = id,
        name = albumName,
        description = description.orEmpty().trim(),
        coverUrl = albumThumbnailAssetId?.let(thumbnailUrl),
        assetCount = assetCount,
        oldestPhotoAt = startDate?.toLocalTime(),
        newestPhotoAt = endDate?.toLocalTime(),
        // Someone else's album is shared with this user even if the server leaves out a name.
        isShared = shared || (owner != null && owner.id != userId),
        sharedBy = sharedBy,
        timeline = TimelineQuery(albumId = id, order = order)
    )
}

// Immich writes these local times as if they were UTC.
private fun Instant.toLocalTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

/**
 * Groups albums by the year of their newest photo, newest year first, with empty albums last.
 * Within a year, the albums with the newest photos come first. This matches the Immich web app's
 * default.
 */
internal fun groupByYear(albums: List<AlbumUi>): List<AlbumGroup> = albums
    .sortedWith(compareByDescending<AlbumUi> { it.newestPhotoAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    .groupBy { it.newestPhotoAt?.year }
    .map { (year, albums) -> AlbumGroup(year, albums) }

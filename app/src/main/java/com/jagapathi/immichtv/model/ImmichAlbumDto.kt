package com.jagapathi.immichtv.model

import com.jagapathi.immichtv.util.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * An album as returned by `GET /api/albums` and `GET /api/albums/{id}`. Only the fields the app
 * uses are declared.
 */
@Serializable
data class ImmichAlbumDto(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int = 0,
    // An empty string until Immich v4, which sends null instead.
    val description: String? = null,
    /** Whether the album is shared with anyone, by or with this user. */
    val shared: Boolean = false,
    /** When the oldest photo was taken, in local time written as if it were UTC. Missing when empty. */
    @Serializable(with = InstantSerializer::class) val startDate: Instant? = null,
    /** When the newest photo was taken, in local time written as if it were UTC. Missing when empty. */
    @Serializable(with = InstantSerializer::class) val endDate: Instant? = null,
    /** `asc` to show the oldest photos first, `desc` for newest first. */
    val order: String? = null,
    /** The owner, on servers before Immich v3. */
    val owner: ImmichUserDto? = null,
    /** Immich v3 lists the owner first; older servers list only the people it's shared with. */
    val albumUsers: List<ImmichAlbumUserDto> = emptyList()
) {
    val albumOwner: ImmichUserDto? get() = owner ?: albumUsers.firstOrNull()?.user
}

@Serializable
data class ImmichAlbumUserDto(val user: ImmichUserDto)

package com.jagapathi.immichtv.model

import kotlinx.serialization.Serializable

/** An album as returned by `GET /api/albums`. Only the fields the app uses are declared. */
@Serializable
data class ImmichAlbumDto(
    val id: String,
    val albumName: String,
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int = 0,
    val description: String = "",
    val ownerId: String,
    val shared: Boolean = false
)

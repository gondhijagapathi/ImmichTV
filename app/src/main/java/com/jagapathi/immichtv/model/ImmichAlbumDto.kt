package com.jagapathi.immichtv.model

import kotlinx.serialization.Serializable

@Serializable
data class ImmichAlbumDto(
    val assetId: String,
    val id: String,
    val isOwned: Boolean,
    val isShared: Boolean,
    val name: String
)
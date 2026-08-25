package com.jagapathi.immichtv.model

import com.jagapathi.immichtv.util.DateSerializer
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class ImmichPeopleDto(
    val hasNextPage: Boolean,
    val hidden: Int,
    val people: List<ImmichPersonResponseDto>,
    val total: Int
)

@Serializable
data class ImmichPersonResponseDto(
    @Serializable(with = DateSerializer::class) val birthDate: Date? = null,
    val color: String? = null,
    val id: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val name: String = "",
    val thumbnailPath: String? = null,
    @Serializable(with = DateSerializer::class) val updatedAt: Date? = null
)

package com.jagapathi.immichtv.model

import com.jagapathi.immichtv.util.InstantSerializer
import com.jagapathi.immichtv.util.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

@Serializable
data class ImmichPeopleDto(
    // Missing on servers from before the people list was paginated.
    val hasNextPage: Boolean = false,
    val hidden: Int = 0,
    val people: List<ImmichPersonResponseDto>,
    val total: Int = 0
)

@Serializable
data class ImmichPersonResponseDto(
    @Serializable(with = LocalDateSerializer::class) val birthDate: LocalDate? = null,
    val color: String? = null,
    val id: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val name: String = "",
    val thumbnailPath: String? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null
)

@Serializable
data class ImmichPersonStatisticsDto(
    /** How many photos and videos show the person. */
    val assets: Int = 0
)

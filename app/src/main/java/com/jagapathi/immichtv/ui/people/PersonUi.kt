package com.jagapathi.immichtv.ui.people

import com.jagapathi.immichtv.model.ImmichPersonResponseDto
import com.jagapathi.immichtv.model.TimelineQuery
import java.time.LocalDate

/** A person as the People tab and the person page show them. */
data class PersonUi(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val birthDate: LocalDate?,
    /** The photos the person is in, as the person page of the Immich web app shows them. */
    val timeline: TimelineQuery
)

internal fun ImmichPersonResponseDto.toPersonUi(thumbnailUrl: String) = PersonUi(
    id = id,
    name = name.trim(),
    thumbnailUrl = thumbnailUrl,
    birthDate = birthDate,
    timeline = TimelineQuery(personId = id, visibility = "timeline", withPartners = true)
)

/**
 * The people to list on the People tab: those with a name who aren't hidden, favorites first.
 * Otherwise the server's order is kept, which puts the people in the most photos first.
 */
internal fun List<ImmichPersonResponseDto>.listedPeople(): List<ImmichPersonResponseDto> =
    filter { it.name.isNotBlank() && !it.isHidden }.sortedByDescending { it.isFavorite }

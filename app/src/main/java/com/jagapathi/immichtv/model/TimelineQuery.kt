package com.jagapathi.immichtv.model

/**
 * Which assets a timeline shows. The fields are the filters of Immich's timeline endpoints, so the
 * same timeline UI can show the whole library, an album, a person, or favorites.
 */
data class TimelineQuery(
    val albumId: String? = null,
    val personId: String? = null,
    val isFavorite: Boolean? = null,
    /** e.g. `timeline` to leave out archived photos. Null lets the server pick its default. */
    val visibility: String? = null,
    /** Include photos from partners who share their library. Needs `visibility = "timeline"`. */
    val withPartners: Boolean = false,
    /** Show only the cover photo of each stack, like the Immich apps do. */
    val withStacked: Boolean = false,
    /** `asc` for oldest first, or `desc`, the server's default, for newest first. */
    val order: String? = null
) {
    companion object {
        /** The user's whole library, like the Photos tab of the Immich apps. */
        val Library = TimelineQuery(visibility = "timeline", withPartners = true, withStacked = true)
    }
}

package com.jagapathi.immichtv.ui.albums

import android.icu.text.DateFormat
import android.icu.text.DateIntervalFormat
import android.icu.util.DateInterval
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.jagapathi.immichtv.R
import java.text.FieldPosition
import java.time.LocalDate
import java.time.ZoneOffset

/** "Shared by Asha" for someone else's album, "Shared" for the user's own shared one, else null. */
@Composable
internal fun sharingLabel(album: AlbumUi): String? = when {
    album.sharedBy != null -> stringResource(R.string.album_shared_by, album.sharedBy)
    album.isShared -> stringResource(R.string.album_shared)
    else -> null
}

/** When the album's photos were taken, e.g. "Mar 3 – 18, 2024", or null if it has none. */
@Composable
internal fun photoDates(album: AlbumUi): String? {
    val locale = LocalConfiguration.current.locales[0]
    val from = album.oldestPhotoAt?.toLocalDate() ?: return null
    val to = album.newestPhotoAt?.toLocalDate() ?: from
    return remember(from, to, locale) {
        val format = DateIntervalFormat.getInstance(DateFormat.YEAR_ABBR_MONTH_DAY, locale)
        format.setTimeZone(TimeZone.GMT_ZONE)
        fun millis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        format.format(DateInterval(millis(from), millis(to)), StringBuffer(), FieldPosition(0)).toString()
    }
}

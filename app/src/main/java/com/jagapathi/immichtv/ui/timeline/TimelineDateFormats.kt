package com.jagapathi.immichtv.ui.timeline

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date formats for the timeline, following the device's language and 12/24-hour setting. */
internal class TimelineDateFormats(private val locale: Locale, is24Hour: Boolean) {
    private fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

    private val monthFormat = pattern("MMMMyyyy")
    private val dayFormat = pattern("EEEMMMd")
    private val dayWithYearFormat = pattern("EEEMMMdyyyy")
    private val fullDateFormat = pattern("EEEEMMMMdyyyy")
    private val timeFormat = pattern(if (is24Hour) "Hmm" else "hmma")

    /** e.g. "September 2025". */
    fun month(yearMonth: YearMonth): String = monthFormat.format(yearMonth)

    /** e.g. "Mon, Sep 22", with the year added outside the current one. */
    fun day(date: LocalDate, today: LocalDate): String =
        (if (date.year == today.year) dayFormat else dayWithYearFormat).format(date)

    /** e.g. "Monday, September 22, 2025". */
    fun fullDate(dateTime: LocalDateTime): String = fullDateFormat.format(dateTime)

    /** e.g. "2:32 PM" or "14:32". */
    fun time(dateTime: LocalDateTime): String = timeFormat.format(dateTime)
}

@Composable
internal fun rememberTimelineDateFormats(): TimelineDateFormats {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { TimelineDateFormats(locale, is24Hour) }
}

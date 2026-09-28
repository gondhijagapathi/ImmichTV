package com.jagapathi.immichtv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import com.jagapathi.immichtv.R
import java.text.NumberFormat

/** How many photos and videos there are, e.g. "1 item" or "1,234 items". */
@Composable
internal fun itemCount(count: Int): String {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    return pluralStringResource(R.plurals.item_count, count, numberFormat.format(count))
}

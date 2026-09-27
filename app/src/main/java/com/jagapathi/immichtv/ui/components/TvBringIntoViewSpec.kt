package com.jagapathi.immichtv.ui.components

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Keeps the focused row away from the top and bottom edges while moving through a grid, so the
 * next row, or the heading above, is always in sight.
 */
val TvBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.2f
        val start = offset - margin
        val end = offset + size - (containerSize - margin)
        return when {
            size > containerSize - 2 * margin -> start
            start < 0 -> start
            end > 0 -> end
            else -> 0f
        }
    }
}

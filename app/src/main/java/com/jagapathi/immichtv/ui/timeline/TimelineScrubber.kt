package com.jagapathi.immichtv.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How far the grid is scrolled: into which month, and how far through it (0 to 1). */
internal data class ScrubberPosition(val month: Int, val progress: Float)

/**
 * The TV take on the Immich apps' draggable timeline scrollbar. A rail on the right shows where
 * the grid is, with a label for each year; each month gets space in proportion to its photos.
 *
 * Pressing right from the grid focuses the rail. Up and down then step through the months while
 * the grid follows ([onMonthPreview]), and OK or left jumps into the chosen month
 * ([onMonthSelected]).
 */
@Composable
internal fun TimelineScrubber(
    months: List<TimelineMonth>,
    position: ScrubberPosition,
    isScrolling: Boolean,
    onMonthPreview: (month: Int) -> Unit,
    onMonthSelected: (month: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val formats = rememberTimelineDateFormats()
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableIntStateOf(0) }
    var hasMoved by remember { mutableStateOf(false) }

    // Where each month starts on the rail, from 0 (top) to 1 (bottom).
    val monthTops = remember(months) {
        val total = months.sumOf { it.size }.coerceAtLeast(1).toFloat()
        var before = 0
        FloatArray(months.size) { i -> (before / total).also { before += months[i].size } }
    }
    fun monthTop(month: Int) = monthTops.getOrElse(month) { 1f }

    val thumbFraction = if (isFocused) {
        monthTop(selectedMonth)
    } else {
        val start = monthTop(position.month)
        start + (monthTop(position.month + 1) - start) * position.progress
    }

    // Show the month name while scrolling, and for a moment after.
    var showLabelAfterScroll by remember { mutableStateOf(false) }
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            showLabelAfterScroll = true
        } else {
            delay(LabelLingerMillis)
            showLabelAfterScroll = false
        }
    }
    val labelMonth = (if (isFocused) selectedMonth else position.month).coerceIn(months.indices)

    BoxWithConstraints(modifier = modifier.width(LabelAreaWidth + RailWidth)) {
        val railHeight = maxHeight - RailVerticalPadding * 2
        val thumbY = railHeight * thumbFraction

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(RailWidth)
                .fillMaxHeight()
                .onFocusChanged { state ->
                    if (state.isFocused && !isFocused) {
                        selectedMonth = position.month
                        hasMoved = false
                    }
                    isFocused = state.isFocused
                }
                .onPreviewKeyEvent { event ->
                    val isSelectKey = event.key == Key.DirectionCenter || event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                    if (event.type == KeyEventType.KeyUp) {
                        // Act on release, so the tile that gets focus doesn't also see the key.
                        if (isSelectKey) {
                            if (hasMoved) onMonthSelected(selectedMonth) else focusManager.moveFocus(FocusDirection.Left)
                        }
                        return@onPreviewKeyEvent isSelectKey
                    }
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> {
                            // At the newest month, let focus move up to the navigation bar.
                            if (selectedMonth == 0) return@onPreviewKeyEvent false
                            selectedMonth--
                            hasMoved = true
                            onMonthPreview(selectedMonth)
                            true
                        }
                        Key.DirectionDown -> {
                            if (selectedMonth < months.lastIndex) {
                                selectedMonth++
                                hasMoved = true
                                onMonthPreview(selectedMonth)
                            }
                            true
                        }
                        // Without a new month picked, left just goes back to the focused photo.
                        Key.DirectionLeft -> if (hasMoved) {
                            onMonthSelected(selectedMonth)
                            true
                        } else {
                            false
                        }
                        Key.DirectionRight -> true
                        else -> isSelectKey
                    }
                }
                .focusable()
                .background(
                    color = if (isFocused) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(RailWidth / 2)
                )
                .padding(vertical = RailVerticalPadding)
        ) {
            RailTrack(monthTops = monthTops)
            YearLabels(months = months, monthTops = monthTops, railHeight = railHeight)
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = (thumbY - ThumbHeight / 2).roundToPx()) }
                    .padding(start = TrackX - ThumbWidth / 2)
                    .size(ThumbWidth, ThumbHeight)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(ThumbHeight / 2))
            )
        }

        if (isFocused || showLabelAfterScroll) {
            val labelY = (thumbY + RailVerticalPadding - LabelHeight / 2).coerceIn(0.dp, maxHeight - LabelHeight)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = -RailWidth.roundToPx(), y = labelY.roundToPx()) }
                    .height(LabelHeight)
                    .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(LabelHeight / 2))
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = formats.month(months[labelMonth].yearMonth),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    }
}

/** The track line, with a dot where each month starts when there's room for it. */
@Composable
private fun RailTrack(monthTops: FloatArray) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val dotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val x = TrackX.toPx()
        drawLine(trackColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
        var lastDotY = Float.NEGATIVE_INFINITY
        for (top in monthTops) {
            val y = top * size.height
            if (y - lastDotY < MinDotGap.toPx()) continue
            drawCircle(dotColor, radius = 2.dp.toPx(), center = Offset(x, y))
            lastDotY = y
        }
    }
}

/** A label at the top of each year's newest month, skipping any that would overlap. */
@Composable
private fun YearLabels(months: List<TimelineMonth>, monthTops: FloatArray, railHeight: Dp) {
    val minGap = with(LocalDensity.current) { MinYearLabelGap.toPx() }
    val railHeightPx = with(LocalDensity.current) { railHeight.toPx() }
    var lastY = Float.NEGATIVE_INFINITY
    var lastYear: Int? = null
    months.forEachIndexed { i, month ->
        val year = month.yearMonth.year
        if (year == lastYear) return@forEachIndexed
        lastYear = year
        val y = monthTops[i] * railHeightPx
        if (y - lastY < minGap) return@forEachIndexed
        lastY = y
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset { IntOffset(x = 0, y = y.roundToInt() - YearLabelOffset.roundToPx()) }
                .padding(start = 4.dp)
        )
    }
}

private val RailWidth = TimelineDefaults.ScrubberWidth
private val RailVerticalPadding = 16.dp
private val TrackX = 50.dp
private val ThumbWidth = 22.dp
private val ThumbHeight = 4.dp
private val LabelAreaWidth = 200.dp
private val LabelHeight = 32.dp
private val MinDotGap = 8.dp
private val MinYearLabelGap = 20.dp
private val YearLabelOffset = 6.dp
private const val LabelLingerMillis = 1200L

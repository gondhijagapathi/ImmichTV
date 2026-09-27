package com.jagapathi.immichtv.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.model.TimelineQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration

/**
 * A photo library screen in the style of the Immich apps: a grid of photos grouped by month and
 * day, a scrubber on the right for jumping through time, and a full-screen viewer.
 *
 * Everything is driven by [query], so the same component can show the whole library, an album,
 * a person or favorites. [header] is shown above the photos and scrolls with them, e.g. for
 * memories.
 */
@Composable
fun PhotoTimeline(
    query: TimelineQuery,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null
) {
    val viewModel = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
        key = "PhotoTimeline:$query"
    ) { factory -> factory.create(query) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            TimelineUiState.Loading -> CircularProgressIndicator()
            is TimelineUiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = viewModel::retry) {
                    Text(stringResource(R.string.retry))
                }
            }
            is TimelineUiState.Ready -> if (current.layout.months.isEmpty() && header == null) {
                Text(
                    text = stringResource(R.string.timeline_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                TimelineContent(layout = current.layout, viewModel = viewModel, header = header)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineContent(
    layout: TimelineLayout,
    viewModel: TimelineViewModel,
    header: (@Composable () -> Unit)?
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)
    // Grid indices are shifted by one when there's a header above the photos.
    val itemOffset = if (header != null) 1 else 0

    // A tile asks for focus when it's composed with this key, e.g. after closing the viewer.
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }
    var viewerPosition by remember { mutableStateOf<AssetPosition?>(null) }
    val monthError by viewModel.monthError.collectAsStateWithLifecycle()

    // Load the months on screen plus one either side, so moving on rarely waits for the network.
    LaunchedEffect(gridState, itemOffset) {
        snapshotFlow {
            val visible = gridState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                IntRange.EMPTY
            } else {
                val first = currentLayout.monthAt(visible.first().index - itemOffset)
                val last = currentLayout.monthAt(visible.last().index - itemOffset)
                (first - 1)..(last + 1)
            }
        }.distinctUntilChanged().collectLatest { range ->
            // Skip months that only flash by while scrubbing.
            delay(MonthLoadDelayMillis)
            viewModel.loadMonths(range)
        }
    }

    fun focusTile(position: AssetPosition) {
        val layoutNow = currentLayout
        val index = itemOffset + (layoutNow.itemIndexOf(position) ?: return)
        scope.launch {
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                gridState.scrollToItem(index)
            }
            pendingFocusKey = layoutNow.tileKey(position)
        }
    }

    val isScrolledDown by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    // Back from deep in the timeline returns to the newest photos, as on other TV apps.
    BackHandler(enabled = isScrolledDown && viewerPosition == null) {
        scope.launch {
            gridState.scrollToItem(0)
            if (currentLayout.assetCount > 0) pendingFocusKey = currentLayout.tileKey(AssetPosition(0, 0))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(TimelineDefaults.Columns),
                state = gridState,
                contentPadding = PaddingValues(
                    start = TimelineDefaults.HorizontalPadding,
                    end = TimelineDefaults.ScrubberWidth,
                    bottom = TimelineDefaults.HorizontalPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(TimelineDefaults.TileSpacing),
                verticalArrangement = Arrangement.spacedBy(TimelineDefaults.TileSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer()
            ) {
                if (header != null) {
                    item(key = HeaderKey, span = { GridItemSpan(maxLineSpan) }, contentType = HeaderKey) {
                        // Rows inside the header scroll the usual way.
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                            header()
                        }
                    }
                }
                items(
                    count = layout.items.size,
                    key = { layout.items[it].key },
                    span = {
                        if (layout.items[it] is TimelineItem.Tile) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                    },
                    contentType = { layout.items[it]::class }
                ) { index ->
                    when (val item = layout.items[index]) {
                        is TimelineItem.MonthHeader -> MonthHeader(item.yearMonth)
                        is TimelineItem.DayHeader -> DayHeader(item.date)
                        is TimelineItem.Tile -> AssetTile(
                            asset = item.asset,
                            thumbnailUrl = item.asset?.let { viewModel.thumbnailUrl(it.id) },
                            requestFocus = pendingFocusKey == item.key,
                            onFocusRequested = { pendingFocusKey = null },
                            onClick = { viewerPosition = item.position }
                        )
                    }
                }
            }
        }

        if (layout.months.size > 1) {
            GridScrubber(
                layout = layout,
                gridState = gridState,
                itemOffset = itemOffset,
                onMonthPreview = { month ->
                    scope.launch { gridState.scrollToItem(itemOffset + currentLayout.monthHeaderIndex(month)) }
                },
                onMonthSelected = { month -> focusTile(AssetPosition(month, 0)) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }

        AnimatedVisibility(
            visible = monthError != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = monthError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    viewerPosition?.let { start ->
        AssetViewer(
            layout = layout,
            startPosition = start,
            thumbnailUrl = viewModel::thumbnailUrl,
            previewUrl = viewModel::previewUrl,
            onLoadMonth = { month -> viewModel.loadMonths(month..month) },
            onDismiss = { last ->
                viewerPosition = null
                focusTile(last)
            }
        )
    }
}

/** Connects the scrubber to the grid. Scroll position is read here so only the scrubber redraws. */
@Composable
private fun GridScrubber(
    layout: TimelineLayout,
    gridState: LazyGridState,
    itemOffset: Int,
    onMonthPreview: (month: Int) -> Unit,
    onMonthSelected: (month: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLayout by rememberUpdatedState(layout)
    val position by remember(gridState, itemOffset) {
        derivedStateOf {
            val index = gridState.firstVisibleItemIndex - itemOffset
            ScrubberPosition(currentLayout.monthAt(index), currentLayout.progressInMonth(index))
        }
    }
    TimelineScrubber(
        months = layout.months,
        position = position,
        isScrolling = gridState.isScrollInProgress,
        onMonthPreview = onMonthPreview,
        onMonthSelected = onMonthSelected,
        modifier = modifier
    )
}

@Composable
private fun MonthHeader(yearMonth: YearMonth) {
    val formats = rememberTimelineDateFormats()
    Text(
        text = formats.month(yearMonth),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun DayHeader(date: LocalDate) {
    val formats = rememberTimelineDateFormats()
    val today = remember { LocalDate.now() }
    val text = when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> formats.day(date, today)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun AssetTile(
    asset: TimelineAsset?,
    thumbnailUrl: String?,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    if (requestFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(TimelineDefaults.TileShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = TimelineDefaults.TileShape)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            // Draw the enlarged, focused tile over its neighbours.
            .zIndex(if (isFocused) 1f else 0f)
            .aspectRatio(1f)
            .focusRequester(focusRequester)
    ) {
        if (asset == null) return@Surface

        val context = LocalPlatformContext.current
        val request = remember(thumbnailUrl) {
            // An explicit cache key lets the viewer reuse this thumbnail while its preview loads.
            ImageRequest.Builder(context).data(thumbnailUrl).memoryCacheKey(thumbnailUrl).build()
        }
        val placeholder = remember(asset.thumbhash) {
            thumbHashBitmap(asset.thumbhash)?.let { BitmapPainter(it.asImageBitmap()) }
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            placeholder = placeholder,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (asset.duration != null || asset.isFavorite) {
            // Keeps the white badges readable on bright photos.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.3f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.35f)
                        )
                    )
            )
        }
        asset.duration?.let { duration ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow),
                    contentDescription = stringResource(R.string.video),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (asset.isFavorite) {
            Icon(
                painter = painterResource(R.drawable.ic_favorite),
                contentDescription = stringResource(R.string.favorite),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
    }
}

/** e.g. 0:07, 12:34 or 1:02:03. */
internal fun formatDuration(duration: Duration): String = duration.toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/**
 * Keeps the focused row away from the top and bottom edges while moving through the grid, so the
 * next row, or the date above, is always in sight.
 */
@OptIn(ExperimentalFoundationApi::class)
private val TvBringIntoViewSpec = object : BringIntoViewSpec {
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

private const val HeaderKey = "header"
private const val MonthLoadDelayMillis = 150L

internal object TimelineDefaults {
    const val Columns = 7
    val HorizontalPadding = 48.dp
    val ScrubberWidth = 64.dp
    val TileSpacing = 6.dp
    val TileShape = RoundedCornerShape(4.dp)
}

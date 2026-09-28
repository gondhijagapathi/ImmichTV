package com.jagapathi.immichtv.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.size.SizeResolver
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.ui.components.ColumnFocus
import com.jagapathi.immichtv.ui.components.ErrorMessage
import com.jagapathi.immichtv.ui.components.TvBringIntoViewSpec
import com.jagapathi.immichtv.ui.components.clipDrawing
import com.jagapathi.immichtv.ui.components.columnFocus
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
 * a person or favorites. [emptyText] is shown when there are no photos, and [header] above the
 * photos, scrolling with them, e.g. for memories.
 *
 * [requestInitialFocus] focuses the first photo once loaded, or the retry button if loading fails,
 * for screens where the timeline is the only thing to focus.
 */
@Composable
fun PhotoTimeline(
    query: TimelineQuery,
    modifier: Modifier = Modifier,
    emptyText: String = stringResource(R.string.timeline_empty),
    requestInitialFocus: Boolean = false,
    header: (@Composable () -> Unit)? = null
) {
    val viewModel = hiltViewModel<TimelineViewModel, TimelineViewModel.Factory>(
        key = "PhotoTimeline:$query"
    ) { factory -> factory.create(query) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            TimelineUiState.Loading -> CircularProgressIndicator()
            is TimelineUiState.Error -> ErrorMessage(
                message = current.message,
                actionLabel = stringResource(R.string.retry),
                onAction = viewModel::retry,
                requestFocus = requestInitialFocus
            )
            is TimelineUiState.Ready -> if (current.layout.months.isEmpty() && header == null) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                TimelineContent(
                    layout = current.layout,
                    viewModel = viewModel,
                    requestInitialFocus = requestInitialFocus,
                    header = header
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimelineContent(
    layout: TimelineLayout,
    viewModel: TimelineViewModel,
    requestInitialFocus: Boolean,
    header: (@Composable () -> Unit)?
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentLayout by rememberUpdatedState(layout)
    // List indices are shifted by one when there's a header above the photos.
    val itemOffset = if (header != null) 1 else 0

    // A tile asks for focus when it's composed with this key, e.g. after closing the viewer.
    var pendingFocusKey by remember { mutableStateOf<String?>(null) }
    // Focuses the grid's last focused photo, through its focusRestorer.
    val gridFocusRequester = remember { FocusRequester() }
    var viewerPosition by remember { mutableStateOf<AssetPosition?>(null) }
    val monthError by viewModel.monthError.collectAsStateWithLifecycle()

    // Made once here rather than in each header: building the formats is slow.
    val dateFormats = rememberTimelineDateFormats()
    val today = remember { LocalDate.now() }

    // Shared by every tile, so tiles whose photo didn't change skip recomposing when a month loads.
    val openViewer = remember { { position: AssetPosition -> viewerPosition = position } }
    val onTileFocusRequested = remember { { pendingFocusKey = null } }
    val badges = rememberTileBadges()
    val columnFocus = remember {
        ColumnFocus(isScrolling = { listState.isScrollInProgress }) { currentLayout.gridRows() }
    }

    // Load the months on screen plus one either side, so moving on rarely waits for the network.
    LaunchedEffect(listState, itemOffset) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
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
        val index = itemOffset + (layoutNow.rowIndexOf(position) ?: return)
        scope.launch {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                listState.scrollToItem(index)
            }
            pendingFocusKey = layoutNow.tileKey(position)
        }
    }

    if (requestInitialFocus) {
        LaunchedEffect(Unit) {
            // Unless the grid was restored to somewhere further down.
            if (listState.firstVisibleItemIndex == 0 && currentLayout.assetCount > 0) {
                pendingFocusKey = currentLayout.tileKey(AssetPosition(0, 0))
            }
        }
    }

    val isScrolledDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    // Back from deep in the timeline returns to the newest photos, as on other TV apps.
    BackHandler(enabled = isScrolledDown && viewerPosition == null) {
        scope.launch {
            listState.scrollToItem(0)
            if (currentLayout.assetCount > 0) pendingFocusKey = currentLayout.tileKey(AssetPosition(0, 0))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoViewSpec) {
            // A row of photos at a time: a lazy grid of single photos was too slow to scroll on TVs,
            // with the lazy layout's work for each item and, up to Android 9, a View behind each.
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = TimelineDefaults.HorizontalPadding,
                    end = TimelineDefaults.ScrubberWidth,
                    bottom = TimelineDefaults.HorizontalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(TimelineDefaults.TileSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(gridFocusRequester)
                    .focusRestorer()
            ) {
                if (header != null) {
                    item(key = HeaderKey, contentType = HeaderKey) {
                        // Rows inside the header scroll the usual way.
                        CompositionLocalProvider(LocalBringIntoViewSpec provides defaultBringIntoViewSpec) {
                            header()
                        }
                    }
                }
                items(
                    count = layout.rows.size,
                    key = { layout.rows[it].key },
                    contentType = { layout.rows[it].contentType() }
                ) { index ->
                    when (val row = layout.rows[index]) {
                        is TimelineItem.MonthHeader -> MonthHeader(row.yearMonth, dateFormats)
                        is TimelineItem.DayHeader -> DayHeader(row.date, today, dateFormats)
                        is TileRow -> TileRow {
                            for (tile in row.tiles) {
                                key(tile.key) {
                                    AssetTile(
                                        asset = tile.asset,
                                        thumbnailUrl = tile.asset?.let { viewModel.thumbnailUrl(it.id) },
                                        position = tile.position,
                                        requestFocus = pendingFocusKey == tile.key,
                                        onFocusRequested = onTileFocusRequested,
                                        onClick = openViewer,
                                        columnFocus = columnFocus,
                                        badges = badges
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (layout.months.size > 1) {
            GridScrubber(
                layout = layout,
                listState = listState,
                itemOffset = itemOffset,
                onMonthPreview = { month ->
                    scope.launch { listState.scrollToItem(itemOffset + currentLayout.monthHeaderIndex(month)) }
                },
                onMonthSelected = { month -> focusTile(AssetPosition(month, 0)) },
                onReturn = { gridFocusRequester.requestFocus() },
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
            videoUrl = viewModel::videoUrl,
            createPlayer = viewModel::createVideoPlayer,
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
    listState: LazyListState,
    itemOffset: Int,
    onMonthPreview: (month: Int) -> Unit,
    onMonthSelected: (month: Int) -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLayout by rememberUpdatedState(layout)
    val position by remember(listState, itemOffset) {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex - itemOffset
            ScrubberPosition(currentLayout.monthAt(index), currentLayout.progressInMonth(index))
        }
    }
    TimelineScrubber(
        months = layout.months,
        position = position,
        isScrolling = listState.isScrollInProgress,
        onMonthPreview = onMonthPreview,
        onMonthSelected = onMonthSelected,
        onReturn = onReturn,
        modifier = modifier
    )
}

@Composable
private fun MonthHeader(yearMonth: YearMonth, formats: TimelineDateFormats) {
    Text(
        text = formats.month(yearMonth),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, formats: TimelineDateFormats) {
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

/**
 * A photo in the grid: a single layout node, with the thumbnail and badges drawn onto it. Built
 * from plain modifiers rather than a TV Material `Surface` or child composables, which are too
 * heavy for a grid this size: each row that scrolls in composes seven tiles, and every tile on
 * screen is placed again on each frame of scrolling.
 */
@Composable
private fun AssetTile(
    asset: TimelineAsset?,
    thumbnailUrl: String?,
    position: AssetPosition,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onClick: (AssetPosition) -> Unit,
    columnFocus: ColumnFocus<AssetPosition>,
    badges: TileBadges
) {
    val focusRequester = remember { FocusRequester() }

    if (requestFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            onFocusRequested()
        }
    }

    val content = if (asset == null) {
        Modifier
    } else {
        val description = listOfNotNull(
            if (asset.duration != null) stringResource(R.string.video) else null,
            if (asset.isFavorite) stringResource(R.string.favorite) else null
        ).joinToString()
        // Loads the thumbnail at the tile's size, as AsyncImage does.
        val sizeResolver = rememberConstraintsSizeResolver()
        val thumbnail = rememberThumbnailPainter(asset, thumbnailUrl, sizeResolver)
        Modifier
            .then(sizeResolver)
            .paint(thumbnail, sizeToIntrinsics = false, contentScale = ContentScale.Crop)
            .assetBadges(asset, badges)
            .then(if (description.isEmpty()) Modifier else Modifier.semantics { contentDescription = description })
    }
    Box(
        modifier = Modifier
            .focusIndication(focusedGrowth = TileFocusGrowth, border = TileFocusBorder, shape = TimelineDefaults.TileShape)
            .aspectRatio(1f)
            .focusRequester(focusRequester)
            .columnFocus(position, columnFocus)
            .clickable(interactionSource = null, indication = null) { onClick(position) }
            .clipDrawing(TimelineDefaults.TileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(content)
    )
}

/** [asset]'s thumbnail, over its blurry placeholder until it loads. */
@Composable
private fun rememberThumbnailPainter(asset: TimelineAsset, thumbnailUrl: String?, sizeResolver: SizeResolver): Painter {
    val context = LocalPlatformContext.current
    val request = remember(thumbnailUrl, sizeResolver) {
        // An explicit cache key lets the viewer reuse this thumbnail while its preview loads.
        ImageRequest.Builder(context).data(thumbnailUrl).memoryCacheKey(thumbnailUrl).size(sizeResolver).build()
    }
    return rememberAsyncImagePainter(
        model = request,
        placeholder = rememberThumbHashPainter(asset.thumbhash),
        contentScale = ContentScale.Crop
    )
}

// The list asks for these often while scrolling, and `row::class` would allocate each time.
private fun TimelineRow.contentType(): Int = when (this) {
    is TimelineItem.MonthHeader -> 0
    is TimelineItem.DayHeader -> 1
    is TileRow -> 2
}

/**
 * Lays tiles out side by side in [TimelineDefaults.Columns] equal columns, sharing the width out as
 * a grid does, so a short row lines up with full ones.
 */
@Composable
private fun TileRow(content: @Composable () -> Unit) {
    Layout(content = content, measurePolicy = TileRowMeasurePolicy)
}

private val TileRowMeasurePolicy = MeasurePolicy { measurables, constraints ->
    val columns = TimelineDefaults.Columns
    val spacing = TimelineDefaults.TileSpacing.roundToPx()
    // Any pixels left over go to the first columns, one each, as in LazyVerticalGrid.
    val available = constraints.maxWidth - spacing * (columns - 1)
    val placeables = measurables.mapIndexed { column, measurable ->
        measurable.measure(Constraints.fixedWidth(available / columns + if (column < available % columns) 1 else 0))
    }
    layout(constraints.maxWidth, placeables.maxOfOrNull { it.height } ?: 0) {
        var x = 0
        for (placeable in placeables) {
            placeable.place(x, 0)
            x += placeable.width + spacing
        }
    }
}

/** e.g. 0:07, 12:34 or 1:02:03. */
internal fun formatDuration(duration: Duration): String = duration.toComponents { hours, minutes, seconds, _ ->
    if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private const val HeaderKey = "header"
private const val MonthLoadDelayMillis = 150L

private val TileFocusBorder = BorderStroke(3.dp, Color.White)
// Grows into the gap around the tile, leaving about 1dp between its outline and the next tile.
private val TileFocusGrowth = TimelineDefaults.TileSpacing - TileFocusBorder.width / 2 - 1.dp

internal object TimelineDefaults {
    const val Columns = 7
    val HorizontalPadding = 48.dp
    val ScrubberWidth = 72.dp
    val TileSpacing = 6.dp
    val TileShape = RoundedCornerShape(4.dp)
}

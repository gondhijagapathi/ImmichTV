package com.jagapathi.immichtv.ui.albums

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.ui.components.ColumnFocus
import com.jagapathi.immichtv.ui.components.ErrorMessage
import com.jagapathi.immichtv.ui.components.itemCount
import com.jagapathi.immichtv.ui.components.TvBringIntoViewSpec
import com.jagapathi.immichtv.ui.components.columnFocus
import kotlinx.coroutines.flow.first

/**
 * The Albums tab: the user's albums and those shared with them, grouped by the year of their
 * newest photo. [focusAlbumId] is an album to focus once it's on screen, e.g. the one just closed;
 * [onAlbumFocused] reports that it has been.
 */
@Composable
fun AlbumsScreen(
    onAlbumClick: (albumId: String) -> Unit,
    focusAlbumId: String?,
    onAlbumFocused: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            AlbumsUiState.Loading -> CircularProgressIndicator()
            is AlbumsUiState.Error -> ErrorMessage(
                message = current.message,
                actionLabel = stringResource(R.string.retry),
                onAction = viewModel::retry
            )
            is AlbumsUiState.Ready -> if (current.groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.albums_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                AlbumsGrid(
                    groups = current.groups,
                    focusAlbumId = focusAlbumId,
                    onAlbumFocused = onAlbumFocused,
                    onAlbumClick = onAlbumClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsGrid(
    groups: List<AlbumGroup>,
    focusAlbumId: String?,
    onAlbumFocused: () -> Unit,
    onAlbumClick: (albumId: String) -> Unit
) {
    val gridState = rememberLazyGridState()
    val currentOnAlbumFocused by rememberUpdatedState(onAlbumFocused)
    val rows by rememberUpdatedState(remember(groups) { albumRows(groups, AlbumsDefaults.Columns) })
    val columnFocus = remember { ColumnFocus(isScrolling = { gridState.isScrollInProgress }) { rows } }

    // A card can only take focus once it's composed, so scroll it into view first if needed.
    LaunchedEffect(focusAlbumId, groups) {
        if (focusAlbumId == null) return@LaunchedEffect
        val index = gridIndexOf(groups, focusAlbumId)
        if (index == null) {
            // It's gone from the list, so there's nothing to focus.
            currentOnAlbumFocused()
            return@LaunchedEffect
        }
        val visible = snapshotFlow { gridState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
        if (visible.none { it.index == index }) gridState.scrollToItem(index)
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoViewSpec) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(AlbumsDefaults.Columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = AlbumsDefaults.HorizontalPadding,
                end = AlbumsDefaults.HorizontalPadding,
                bottom = AlbumsDefaults.HorizontalPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(AlbumsDefaults.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(AlbumsDefaults.CardSpacing),
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer()
        ) {
            for (group in groups) {
                item(key = "year:${group.year}", span = { GridItemSpan(maxLineSpan) }, contentType = "year") {
                    YearHeader(group.year)
                }
                items(items = group.albums, key = { it.id }, contentType = { "album" }) { album ->
                    AlbumCard(
                        album = album,
                        requestFocus = album.id == focusAlbumId,
                        onFocusRequested = onAlbumFocused,
                        onClick = { onAlbumClick(album.id) },
                        columnFocus = columnFocus
                    )
                }
            }
        }
    }
}

/** Where the album with [albumId] is in the grid, counting year headers, or null if it isn't. */
private fun gridIndexOf(groups: List<AlbumGroup>, albumId: String): Int? {
    var index = 0
    for (group in groups) {
        index++ // The year header.
        val inGroup = group.albums.indexOfFirst { it.id == albumId }
        if (inGroup >= 0) return index + inGroup
        index += group.albums.size
    }
    return null
}

@Composable
private fun YearHeader(year: Int?) {
    Text(
        text = year?.toString() ?: stringResource(R.string.albums_empty_group),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
private fun AlbumCard(
    album: AlbumUi,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onClick: () -> Unit,
    columnFocus: ColumnFocus<String>
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

    // Drawn over its neighbours while enlarged by focus.
    Column(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(AlbumsDefaults.CoverShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = AlbumsDefaults.CoverShape)
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .focusRequester(focusRequester)
                .columnFocus(album.id, columnFocus)
        ) {
            if (album.coverUrl != null) {
                AsyncImage(
                    model = album.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_photo_album),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            // Long names scroll into view while focused.
            overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = if (isFocused) Modifier.basicMarquee() else Modifier
        )
        for (line in listOfNotNull(itemCount(album.assetCount), sharingLabel(album))) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal object AlbumsDefaults {
    const val Columns = 5
    val HorizontalPadding = 48.dp
    val CardSpacing = 24.dp
    val CoverShape = RoundedCornerShape(8.dp)
}

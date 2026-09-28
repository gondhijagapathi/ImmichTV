package com.jagapathi.immichtv.ui.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.ui.components.ErrorMessage
import com.jagapathi.immichtv.ui.components.itemCount
import com.jagapathi.immichtv.ui.timeline.PhotoTimeline

/** An album's page: its name and details pinned at the top, and its photos below. */
@Composable
fun AlbumScreen(
    albumId: String,
    onBack: () -> Unit
) {
    val viewModel = hiltViewModel<AlbumViewModel, AlbumViewModel.Factory> { factory -> factory.create(albumId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            AlbumUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is AlbumUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorMessage(
                    message = current.message,
                    actionLabel = stringResource(if (current.isGone) R.string.back else R.string.retry),
                    onAction = if (current.isGone) onBack else viewModel::retry,
                    requestFocus = true
                )
            }
            is AlbumUiState.Ready -> Column {
                AlbumHeader(current.album)
                PhotoTimeline(
                    query = current.album.timeline,
                    emptyText = stringResource(R.string.album_empty),
                    requestInitialFocus = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: AlbumUi) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 4.dp)
    ) {
        Text(
            text = album.name,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOfNotNull(photoDates(album), itemCount(album.assetCount), sharingLabel(album)).joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (album.description.isNotEmpty()) {
            Text(
                text = album.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    // Long lines are hard to read across a TV.
                    .widthIn(max = 720.dp)
            )
        }
    }
}

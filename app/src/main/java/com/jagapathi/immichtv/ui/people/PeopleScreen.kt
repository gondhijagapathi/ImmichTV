package com.jagapathi.immichtv.ui.people

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.ui.components.ErrorMessage
import com.jagapathi.immichtv.ui.components.TvBringIntoViewSpec
import kotlinx.coroutines.flow.first

/**
 * The People tab: the people named in Immich, favorites first. [focusPersonId] is a person to focus
 * once they're on screen, e.g. the one whose page was just closed; [onPersonFocused] reports that
 * they have been.
 */
@Composable
fun PeopleScreen(
    onPersonClick: (personId: String) -> Unit,
    focusPersonId: String?,
    onPersonFocused: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val current = state) {
            PeopleUiState.Loading -> CircularProgressIndicator()
            is PeopleUiState.Error -> ErrorMessage(
                message = current.message,
                actionLabel = stringResource(R.string.retry),
                onAction = viewModel::retry
            )
            is PeopleUiState.Ready -> if (current.people.isEmpty()) {
                Text(
                    text = stringResource(R.string.people_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            } else {
                PeopleGrid(
                    people = current.people,
                    focusPersonId = focusPersonId,
                    onPersonFocused = onPersonFocused,
                    onPersonClick = onPersonClick
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PeopleGrid(
    people: List<PersonUi>,
    focusPersonId: String?,
    onPersonFocused: () -> Unit,
    onPersonClick: (personId: String) -> Unit
) {
    val gridState = rememberLazyGridState()
    val currentOnPersonFocused by rememberUpdatedState(onPersonFocused)

    // A face can only take focus once it's composed, so scroll it into view first if needed.
    LaunchedEffect(focusPersonId, people) {
        if (focusPersonId == null) return@LaunchedEffect
        val index = people.indexOfFirst { it.id == focusPersonId }
        if (index < 0) {
            // They're gone from the list, so there's nothing to focus.
            currentOnPersonFocused()
            return@LaunchedEffect
        }
        val visible = snapshotFlow { gridState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
        if (visible.none { it.index == index }) gridState.scrollToItem(index)
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvBringIntoViewSpec) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(PeopleDefaults.Columns),
            state = gridState,
            contentPadding = PaddingValues(
                start = PeopleDefaults.HorizontalPadding,
                end = PeopleDefaults.HorizontalPadding,
                // Room for the first row to grow when focused.
                top = 16.dp,
                bottom = PeopleDefaults.HorizontalPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(PeopleDefaults.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(PeopleDefaults.CardSpacing),
            modifier = Modifier
                .fillMaxSize()
                .focusRestorer()
        ) {
            items(items = people, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    requestFocus = person.id == focusPersonId,
                    onFocusRequested = onPersonFocused,
                    onClick = { onPersonClick(person.id) }
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: PersonUi,
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

    // Drawn over its neighbours while enlarged by focus.
    Column(modifier = Modifier.zIndex(if (isFocused) 1f else 0f)) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape)
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .focusRequester(focusRequester)
        ) {
            PersonAvatar(person)
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Centred, including while a long name scrolls into view.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (isFocused) Modifier.basicMarquee() else Modifier
            )
        }
    }
}

internal object PeopleDefaults {
    const val Columns = 6
    val HorizontalPadding = 48.dp
    val CardSpacing = 24.dp
}

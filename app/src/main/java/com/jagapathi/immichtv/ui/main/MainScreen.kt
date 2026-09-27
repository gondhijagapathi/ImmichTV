package com.jagapathi.immichtv.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.ui.albums.AlbumsScreen
import com.jagapathi.immichtv.ui.components.ErrorMessage
import com.jagapathi.immichtv.ui.main.components.MainNavItem
import com.jagapathi.immichtv.ui.main.components.PeopleGrid
import com.jagapathi.immichtv.ui.main.components.TopNavigationBar
import com.jagapathi.immichtv.ui.timeline.PhotoTimeline

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val people by viewModel.people.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainNavItem.Home) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // The album opened from here, kept while it's open. When this screen comes back, its card
    // takes focus from the tabs, which still take it first so that moving up from the grid lands
    // on the selected tab. remember reads it only then, not as the album opens.
    var openedAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    var albumToRefocus by remember { mutableStateOf(openedAlbumId) }

    val logoutSuccess by viewModel.logoutSuccessEvent.collectAsState()

    LaunchedEffect(logoutSuccess) {
        if (logoutSuccess) {
            viewModel.resetLogoutSuccessEvent()
            onLogoutSuccess()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            Box(Modifier.focusGroup().focusRestorer()) {
                TopNavigationBar(
                    selectedItem = selectedTab,
                    onItemSelected = { selectedTab = it },
                    onSettingsClick = onNavigateToSettings,
                    onProfileClick = { showLogoutDialog = true },
                    profilePictureUrl = activeProfile?.profilePictureUrl
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .focusGroup(),
                contentAlignment = Alignment.Center
            ) {
                val tabStateHolder = rememberSaveableStateHolder()
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "SectionTransition"
                ) { targetTab ->
                    // Keeps each tab's scroll position while another tab is showing.
                    tabStateHolder.SaveableStateProvider(targetTab.name) {
                        TabContent(
                            tab = targetTab,
                            people = people,
                            onRetryPeople = viewModel::retry,
                            onOpenAlbum = { albumId ->
                                openedAlbumId = albumId
                                onOpenAlbum(albumId)
                            },
                            albumToFocus = albumToRefocus,
                            onAlbumFocused = {
                                albumToRefocus = null
                                openedAlbumId = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        LogoutDialog(
            profileName = activeProfile?.name,
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TabContent(
    tab: MainNavItem,
    people: PeopleUiState,
    onRetryPeople: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    albumToFocus: String?,
    onAlbumFocused: () -> Unit
) {
    when (tab) {
        MainNavItem.Home -> PhotoTimeline(query = TimelineQuery.Library)
        MainNavItem.Albums -> AlbumsScreen(
            onAlbumClick = onOpenAlbum,
            focusAlbumId = albumToFocus,
            onAlbumFocused = onAlbumFocused
        )
        MainNavItem.People -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            PeopleContent(state = people, onRetry = onRetryPeople)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PeopleContent(
    state: PeopleUiState,
    onRetry: () -> Unit
) {
    when (state) {
        PeopleUiState.Loading -> CircularProgressIndicator()
        is PeopleUiState.Error -> ErrorMessage(
            message = state.message,
            actionLabel = stringResource(R.string.retry),
            onAction = onRetry
        )
        is PeopleUiState.Success -> if (state.people.isEmpty()) {
            Text(
                text = "No named people yet. Name people in Immich to see them here.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        } else {
            PeopleGrid(people = state.people)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LogoutDialog(
    profileName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // A real Dialog gets its own window, so D-pad focus can't wander to the screen behind it.
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Surface(
            modifier = Modifier
                .width(440.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Logout",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Are you sure you want to logout of $profileName?",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(focusRequester)
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(onClick = onConfirm) {
                        Text("Logout")
                    }
                }
            }
        }
    }
}

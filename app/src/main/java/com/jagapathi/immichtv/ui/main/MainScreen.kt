package com.jagapathi.immichtv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.ui.albums.AlbumsScreen
import com.jagapathi.immichtv.ui.main.components.MainNavItem
import com.jagapathi.immichtv.ui.main.components.TopNavigationBar
import com.jagapathi.immichtv.ui.people.PeopleScreen
import com.jagapathi.immichtv.ui.timeline.PhotoTimeline

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenPerson: (personId: String) -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(MainNavItem.Home) }
    val tabFocusRequesters = remember { MainNavItem.entries.associateWith { FocusRequester() } }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // The album or person opened from here, kept while their page is open. When this screen comes
    // back, their card takes focus from the tabs, which still take it first so that moving up from
    // the grid lands on the selected tab. remember reads it only then, not as the page opens.
    var openedItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var itemToRefocus by remember { mutableStateOf(openedItemId) }

    val logoutSuccess by viewModel.logoutSuccessEvent.collectAsState()

    LaunchedEffect(logoutSuccess) {
        if (logoutSuccess) {
            viewModel.resetLogoutSuccessEvent()
            onLogoutSuccess()
        }
    }

    // Back on another tab goes to Home first, as on other TV apps, so only Back on Home leaves the app.
    BackHandler(enabled = selectedTab != MainNavItem.Home) {
        // Focus entering the bar is sent back to where it last was, so it goes to the current tab
        // first. Focusing Home then selects it.
        tabFocusRequesters.getValue(selectedTab).requestFocus()
        tabFocusRequesters.getValue(MainNavItem.Home).requestFocus()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column {
            Box(Modifier.focusGroup().focusRestorer()) {
                TopNavigationBar(
                    selectedItem = selectedTab,
                    onItemSelected = { selectedTab = it },
                    onSettingsClick = onNavigateToSettings,
                    onProfileClick = { showLogoutDialog = true },
                    tabFocusRequesters = tabFocusRequesters,
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
                            onOpenAlbum = { albumId ->
                                openedItemId = albumId
                                onOpenAlbum(albumId)
                            },
                            onOpenPerson = { personId ->
                                openedItemId = personId
                                onOpenPerson(personId)
                            },
                            itemToFocus = itemToRefocus,
                            onItemFocused = {
                                itemToRefocus = null
                                openedItemId = null
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

/** [itemToFocus] is the album or person to focus once their card is on screen. */
@Composable
private fun TabContent(
    tab: MainNavItem,
    onOpenAlbum: (albumId: String) -> Unit,
    onOpenPerson: (personId: String) -> Unit,
    itemToFocus: String?,
    onItemFocused: () -> Unit
) {
    when (tab) {
        MainNavItem.Home -> PhotoTimeline(query = TimelineQuery.Library)
        MainNavItem.Albums -> AlbumsScreen(
            onAlbumClick = onOpenAlbum,
            focusAlbumId = itemToFocus,
            onAlbumFocused = onItemFocused
        )
        MainNavItem.People -> PeopleScreen(
            onPersonClick = onOpenPerson,
            focusPersonId = itemToFocus,
            onPersonFocused = onItemFocused
        )
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

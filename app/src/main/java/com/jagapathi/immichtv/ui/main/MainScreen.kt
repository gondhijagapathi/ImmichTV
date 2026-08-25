package com.jagapathi.immichtv.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.jagapathi.immichtv.ui.main.components.MainNavItem
import com.jagapathi.immichtv.ui.main.components.PeopleGrid
import com.jagapathi.immichtv.ui.main.components.TopNavigationBar

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val people by viewModel.people.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val credentials = activeProfile?.credentials
    var selectedTab by remember { mutableStateOf(MainNavItem.Home) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    
    val logoutSuccess by viewModel.logoutSuccessEvent.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(logoutSuccess) {
        if (logoutSuccess) {
            viewModel.resetLogoutSuccessEvent()
            onLogoutSuccess()
        }
    }

    LaunchedEffect(showLogoutDialog) {
        if (showLogoutDialog) {
            focusRequester.requestFocus()
        }
    }

    if (showLogoutDialog) {
        BackHandler {
            showLogoutDialog = false
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
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "SectionTransition"
                ) { targetTab ->
                    when (targetTab) {
                        MainNavItem.Home -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Welcome to Immich TV!",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Profile Name: ${activeProfile?.name ?: "Not set"}")
                                Text(text = "Profile Pic URL: ${activeProfile?.profilePictureUrl ?: "Not set"}")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Server: ${credentials?.serverUrl ?: "Not set"}")
                                Text(
                                    text = "API Key: ${credentials?.apiKey?.take(5)
                                        ?.let { "$it..." } ?: "Not set"}"
                                )
                            }
                        }
                        MainNavItem.Albums -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Albums Section (Coming Soon)",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                            }
                        }
                        MainNavItem.People -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator()
                                } else {
                                    PeopleGrid(
                                        people = people
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
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
                        text = "Are you sure you want to logout of ${activeProfile?.name}?",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showLogoutDialog = false },
                            modifier = Modifier.focusRequester(focusRequester)
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                showLogoutDialog = false
                                viewModel.logout()
                            }
                        ) {
                            Text("Logout")
                        }
                    }
                }
            }
        }
    }
}

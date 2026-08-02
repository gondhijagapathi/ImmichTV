package com.jagapathi.immichtv.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.jagapathi.immichtv.ui.main.components.MainNavItem
import com.jagapathi.immichtv.ui.main.components.TopNavigationBar

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val credentials = activeProfile?.credentials
    var selectedTab by remember { mutableStateOf(MainNavItem.Home) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

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
            TopNavigationBar(
                selectedItem = selectedTab,
                onItemSelected = { selectedTab = it },
                onSettingsClick = onNavigateToSettings,
                onProfileClick = { showLogoutDialog = true },
                profilePictureUrl = activeProfile?.profilePictureUrl,
                apiKey = activeProfile?.credentials?.apiKey
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    MainNavItem.Home -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                        Text(
                            text = "Albums Section (Coming Soon)",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    MainNavItem.People -> {
                        Text(
                            text = "People Section (Coming Soon)",
                            style = MaterialTheme.typography.headlineMedium
                        )
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

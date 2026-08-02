package com.jagapathi.immichtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.ui.auth.AuthScreen
import com.jagapathi.immichtv.ui.auth.AuthViewModel
import com.jagapathi.immichtv.ui.main.MainScreen
import com.jagapathi.immichtv.ui.main.MainViewModel
import com.jagapathi.immichtv.ui.settings.SettingsScreen
import com.jagapathi.immichtv.ui.settings.SettingsViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Main : Screen("main")
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: PreferenceRepository
) {
    val activeProfile by repository.activeProfile.collectAsState()

    LaunchedEffect(activeProfile) {
        if (activeProfile == null) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(0)
            }
        } else {
            navController.navigate(Screen.Main.route) {
                popUpTo(0)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (activeProfile == null) Screen.Auth.route else Screen.Main.route
    ) {
        composable(Screen.Auth.route) {
            val authViewModel: AuthViewModel = viewModel { AuthViewModel(repository) }
            AuthScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Main.route) {
            val mainViewModel: MainViewModel = viewModel { MainViewModel(repository) }
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(repository) }
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

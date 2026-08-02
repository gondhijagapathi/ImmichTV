package com.jagapathi.immichtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
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
import kotlinx.serialization.Serializable

@Serializable
data object Splash

@Serializable
data object Auth

@Serializable
data object Main

@Serializable
data object Settings

@Composable
fun NavGraph(
    navController: NavHostController,
    repository: PreferenceRepository
) {
    val activeProfile by repository.activeProfile.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Splash
    ) {
        composable<Splash> {
            LaunchedEffect(activeProfile) {
                if (activeProfile == null) {
                    navController.navigate(Auth) {
                        popUpTo(Splash) { inclusive = true }
                    }
                } else {
                    navController.navigate(Main) {
                        popUpTo(Splash) { inclusive = true }
                    }
                }
            }
        }

        composable<Auth> {
            val authViewModel: AuthViewModel = hiltViewModel()

            AuthScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Main) {
                        popUpTo(Auth) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Main> {
            val mainViewModel: MainViewModel = hiltViewModel()
            
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = {
                    navController.navigate(Settings)
                },
                onLogoutSuccess = {
                    navController.navigate(Auth) {
                        popUpTo(Main) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Settings> {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

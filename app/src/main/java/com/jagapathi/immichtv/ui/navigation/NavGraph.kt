package com.jagapathi.immichtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jagapathi.immichtv.ui.auth.AuthScreen
import com.jagapathi.immichtv.ui.auth.AuthViewModel
import com.jagapathi.immichtv.ui.main.MainScreen
import com.jagapathi.immichtv.ui.main.MainViewModel
import com.jagapathi.immichtv.ui.settings.SettingsScreen
import com.jagapathi.immichtv.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
data object Auth

@Serializable
data object Main

@Serializable
data object Settings

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: Any
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
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

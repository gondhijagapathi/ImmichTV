package com.jagapathi.immichtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jagapathi.immichtv.ui.albums.AlbumScreen
import com.jagapathi.immichtv.ui.auth.AuthScreen
import com.jagapathi.immichtv.ui.auth.AuthViewModel
import com.jagapathi.immichtv.ui.main.MainScreen
import com.jagapathi.immichtv.ui.main.MainViewModel
import com.jagapathi.immichtv.ui.people.PersonScreen
import com.jagapathi.immichtv.ui.settings.SettingsScreen
import com.jagapathi.immichtv.ui.settings.SettingsViewModel
import kotlinx.serialization.Serializable

@Serializable
data object Auth

@Serializable
data object Main

@Serializable
data object Settings

@Serializable
data class Album(val id: String)

@Serializable
data class Person(val id: String)

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
        
        composable<Main> { entry ->
            val mainViewModel: MainViewModel = hiltViewModel()

            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = {
                    if (entry.isResumed()) navController.navigate(Settings)
                },
                onOpenAlbum = { albumId ->
                    if (entry.isResumed()) navController.navigate(Album(albumId))
                },
                onOpenPerson = { personId ->
                    if (entry.isResumed()) navController.navigate(Person(personId))
                },
                onLogoutSuccess = {
                    navController.navigate(Auth) {
                        popUpTo(Main) { inclusive = true }
                    }
                }
            )
        }
        
        composable<Settings> { entry ->
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = {
                    if (entry.isResumed()) navController.popBackStack()
                }
            )
        }

        composable<Album> { entry ->
            AlbumScreen(
                albumId = entry.toRoute<Album>().id,
                onBack = {
                    if (entry.isResumed()) navController.popBackStack()
                }
            )
        }

        composable<Person> { entry ->
            PersonScreen(
                personId = entry.toRoute<Person>().id,
                onBack = {
                    if (entry.isResumed()) navController.popBackStack()
                }
            )
        }
    }
}

// A second press while a screen is already leaving would otherwise open or close two screens.
private fun NavBackStackEntry.isResumed() = lifecycle.currentState == Lifecycle.State.RESUMED

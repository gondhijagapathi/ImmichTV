package com.jagapathi.immichtv

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.ui.navigation.Auth
import com.jagapathi.immichtv.ui.navigation.Main
import com.jagapathi.immichtv.ui.navigation.NavGraph
import com.jagapathi.immichtv.ui.theme.ImmichTVTheme

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: PreferenceRepository

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keepSplashScreenUntilSettingsLoad()

        setContent {
            val settings by repository.settings.collectAsStateWithLifecycle()
            val loadedSettings = settings ?: return@setContent
            // Decided once: later logins and logouts navigate explicitly.
            val startDestination = remember { if (loadedSettings.activeProfile != null) Main else Auth }

            ImmichTVTheme(appTheme = loadedSettings.theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )
                }
            }
        }
    }

    /**
     * Holds the system splash screen until settings are read, so the first frame already has
     * the chosen theme and the right screen instead of flashing defaults.
     */
    private fun keepSplashScreenUntilSettingsLoad() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (repository.settings.value == null) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })
    }
}

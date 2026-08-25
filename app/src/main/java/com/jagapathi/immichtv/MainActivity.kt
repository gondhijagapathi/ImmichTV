package com.jagapathi.immichtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.network.ImmichApiService
import com.jagapathi.immichtv.network.LocalImmichApiService
import com.jagapathi.immichtv.ui.navigation.NavGraph
import com.jagapathi.immichtv.ui.theme.ImmichTVTheme

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: PreferenceRepository

    @Inject
    lateinit var apiService: ImmichApiService

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appTheme by repository.theme.collectAsState()
            
            CompositionLocalProvider(LocalImmichApiService provides apiService) {
                ImmichTVTheme(appTheme = appTheme) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        NavGraph(
                            navController = navController,
                            repository = repository
                        )
                    }
                }
            }
        }
    }
}

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
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.ui.navigation.NavGraph
import com.jagapathi.immichtv.ui.theme.ImmichTVTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val repository = PreferenceRepository(applicationContext)

        setContent {
            val appTheme by repository.theme.collectAsState()
            
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

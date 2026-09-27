package com.jagapathi.immichtv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.jagapathi.immichtv.data.AppTheme
import androidx.compose.material3.MaterialTheme as Material3Theme
import androidx.compose.material3.darkColorScheme as material3DarkColorScheme
import androidx.compose.material3.lightColorScheme as material3LightColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmichTVTheme(
    appTheme: AppTheme = AppTheme.System,
    content: @Composable () -> Unit,
) {
    val isInDarkTheme = when (appTheme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }

    val colorScheme = if (isInDarkTheme) {
        darkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80
        )
    } else {
        lightColorScheme(
            primary = Purple40,
            secondary = PurpleGrey40,
            tertiary = Pink40
        )
    }

    // A few components (text fields, progress indicators, menus) only exist in the phone
    // Material3 library and read its theme, so give it the same palette as the TV theme.
    val material3ColorScheme = if (isInDarkTheme) {
        material3DarkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80
        )
    } else {
        material3LightColorScheme(
            primary = Purple40,
            secondary = PurpleGrey40,
            tertiary = Pink40
        )
    }

    Material3Theme(colorScheme = material3ColorScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

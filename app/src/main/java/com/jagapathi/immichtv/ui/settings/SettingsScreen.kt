package com.jagapathi.immichtv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.data.AppTheme

sealed class SettingsCategory(
    val titleRes: Int,
    val icon: ImageVector
) {
    object Appearance : SettingsCategory(R.string.settings_appearance, Icons.Default.Brush)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<SettingsCategory>(SettingsCategory.Appearance) }
    val currentTheme by viewModel.currentTheme.collectAsState()

    BackHandler(onBack = onBackClick)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp)
    ) {
        // Left Side: Categories
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            CategoryItem(
                category = SettingsCategory.Appearance,
                isSelected = selectedCategory == SettingsCategory.Appearance,
                onSelected = { selectedCategory = SettingsCategory.Appearance }
            )
        }

        Spacer(modifier = Modifier.width(48.dp))

        // Right Side: Content
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
        ) {
            when (selectedCategory) {
                is SettingsCategory.Appearance -> {
                    AppearanceSettings(
                        currentTheme = currentTheme,
                        onThemeSelected = { viewModel.setTheme(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(
    category: SettingsCategory,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    Surface(
        onClick = onSelected,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.medium),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppearanceSettings(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.settings_appearance),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = stringResource(R.string.theme),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AppTheme.entries.forEach { theme ->
            ThemeOption(
                theme = theme,
                isSelected = currentTheme == theme,
                onClick = { onThemeSelected(theme) }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ThemeOption(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = theme.name,
                style = MaterialTheme.typography.bodyLarge
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

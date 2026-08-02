package com.jagapathi.immichtv.ui.main.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.DropdownMenu
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jagapathi.immichtv.R

enum class MainNavItem(@StringRes val titleRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Default.Home),
    Albums(R.string.nav_albums, Icons.Default.PhotoAlbum),
    People(R.string.nav_people, Icons.Default.People)
}

/**
 * Default values used by [TopNavigationBar].
 */
object TopBarDefaults {
    val HorizontalPadding = 48.dp
    val VerticalPadding = 24.dp
    val ContainerPaddingHorizontal = 8.dp
    val ContainerPaddingVertical = 4.dp
    val TabItemSpacing = 8.dp
    val TabContentPaddingHorizontal = 16.dp
    val TabContentPaddingVertical = 8.dp
    val IconSize = 20.dp
    val UtilityIconSize = 24.dp
    val ProfileIconSize = 32.dp
    
    const val ContainerAlpha = 0.4f
    const val GradientStartAlpha = 0.9f
    const val GradientMidAlpha = 0.7f
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TopNavigationBar(
    selectedItem: MainNavItem,
    onItemSelected: (MainNavItem) -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    profilePictureUrl: String? = null,
    apiKey: String? = null
) {
    val homeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        homeFocusRequester.requestFocus()
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceColor.copy(alpha = TopBarDefaults.GradientStartAlpha),
                surfaceColor.copy(alpha = TopBarDefaults.GradientMidAlpha),
                Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = TopBarDefaults.VerticalPadding,
                    bottom = TopBarDefaults.VerticalPadding,
                    start = TopBarDefaults.HorizontalPadding,
                    end = TopBarDefaults.HorizontalPadding
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavigationTabs(
                selectedItem = selectedItem,
                onItemSelected = onItemSelected,
                homeFocusRequester = homeFocusRequester
            )

            UtilityActions(
                onSettingsClick = onSettingsClick,
                onProfileClick = onProfileClick,
                profilePictureUrl = profilePictureUrl,
                apiKey = apiKey
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationTabs(
    selectedItem: MainNavItem,
    onItemSelected: (MainNavItem) -> Unit,
    homeFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.wrapContentSize(),
        shape = CircleShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TopBarDefaults.ContainerAlpha)
        )
    ) {
        TabRow(
            selectedTabIndex = selectedItem.ordinal,
            modifier = Modifier.padding(
                horizontal = TopBarDefaults.ContainerPaddingHorizontal,
                vertical = TopBarDefaults.ContainerPaddingVertical
            ),
            indicator = { tabPositions, doesTabRowHaveFocus ->
                TabRowDefaults.PillIndicator(
                    currentTabPosition = tabPositions[selectedItem.ordinal],
                    doesTabRowHaveFocus = doesTabRowHaveFocus
                )
            },
            separator = { Spacer(modifier = Modifier.width(TopBarDefaults.TabItemSpacing)) }
        ) {
            MainNavItem.entries.forEach { item ->
                val isSelected = selectedItem == item
                Tab(
                    selected = isSelected,
                    onFocus = { onItemSelected(item) },
                    onClick = { onItemSelected(item) },
                    colors = TabDefaults.pillIndicatorTabColors(),
                    modifier = if (item == MainNavItem.Home) {
                        Modifier.focusRequester(homeFocusRequester)
                    } else {
                        Modifier
                    }
                ) {
                    TabContent(item = item)
                }
            }
        }
    }
}

@Composable
private fun TabContent(item: MainNavItem) {
    Row(
        modifier = Modifier.padding(
            horizontal = TopBarDefaults.TabContentPaddingHorizontal,
            vertical = TopBarDefaults.TabContentPaddingVertical
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(TopBarDefaults.IconSize)
        )
        Spacer(modifier = Modifier.width(TopBarDefaults.TabItemSpacing))
        Text(
            text = stringResource(item.titleRes),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UtilityActions(
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    profilePictureUrl: String? = null,
    apiKey: String? = null
) {
    var showProfileMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.wrapContentSize(),
        shape = CircleShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TopBarDefaults.ContainerAlpha)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = TopBarDefaults.ContainerPaddingHorizontal,
                vertical = TopBarDefaults.ContainerPaddingVertical
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TopBarDefaults.TabItemSpacing)
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.clip(CircleShape),
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(TopBarDefaults.UtilityIconSize)
                )
            }

            Box {
                IconButton(
                    onClick = { showProfileMenu = true },
                    modifier = Modifier.clip(CircleShape),
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                    )
                ) {
                    val context = LocalContext.current
                    val imageRequest = remember(profilePictureUrl, apiKey) {
                        ImageRequest.Builder(context)
                            .data(profilePictureUrl)
                            .apply {
                                if (apiKey != null) {
                                    addHeader("x-api-key", apiKey)
                                }
                            }
                            .crossfade(true)
                            .build()
                    }

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = stringResource(R.string.profile),
                        modifier = Modifier
                            .size(TopBarDefaults.ProfileIconSize)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Default.AccountCircle),
                        error = rememberVectorPainter(Icons.Default.AccountCircle)
                    )
                }

                DropdownMenu(
                    expanded = showProfileMenu,
                    onDismissRequest = { showProfileMenu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Surface(
                        onClick = {
                            showProfileMenu = false
                            onProfileClick()
                        },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(TopBarDefaults.IconSize)
                            )
                            Spacer(modifier = Modifier.width(TopBarDefaults.TabItemSpacing))
                            Text(
                                text = stringResource(R.string.sign_out),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

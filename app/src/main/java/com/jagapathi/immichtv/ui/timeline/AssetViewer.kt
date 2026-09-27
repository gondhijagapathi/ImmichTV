package com.jagapathi.immichtv.ui.timeline

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.jagapathi.immichtv.R
import kotlinx.coroutines.delay
import java.text.NumberFormat

/**
 * Shows one asset at a time over the whole screen, playing videos. Back closes it, reporting where
 * the viewer ended up so the grid can focus that photo. See [viewerActionFor] for what each key does.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AssetViewer(
    layout: TimelineLayout,
    startPosition: AssetPosition,
    thumbnailUrl: (assetId: String) -> String?,
    previewUrl: (assetId: String) -> String?,
    videoUrl: (assetId: String) -> String?,
    createPlayer: () -> Player,
    onLoadMonth: (month: Int) -> Unit,
    onDismiss: (lastPosition: AssetPosition) -> Unit
) {
    var requestedPosition by remember { mutableStateOf(startPosition) }
    // The month may turn out smaller than the server's count once it loads.
    val position = layout.coerce(requestedPosition)
    val asset = layout.assetAt(position)
    val isVideo = asset?.isImage == false
    val previous = layout.positionBefore(position)
    val next = layout.positionAfter(position)

    // One player for every video shown while the viewer is open.
    val player = remember { createPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    val videoUri = if (isVideo) videoUrl(asset.id) else null
    LaunchedEffect(videoUri) {
        if (videoUri == null) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.setMediaItem(MediaItem.fromUri(videoUri))
            player.prepare()
            player.play()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    val playPause = rememberPlayPauseButtonState(player)

    // Bumped to show the details again and restart the timer that hides them.
    var detailsShownAt by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(true) }
    // A video that's paused, finished or failed keeps its controls on screen.
    val keepDetails = isVideo && playPause.showPlay
    LaunchedEffect(detailsShownAt, keepDetails) {
        showDetails = true
        if (keepDetails) return@LaunchedEffect
        delay(DetailsVisibleMillis)
        showDetails = false
    }

    LaunchedEffect(position.month, asset == null) {
        if (asset == null) onLoadMonth(position.month)
    }
    PreloadNeighbours(layout, position, previewUrl, onLoadMonth)

    fun show(newPosition: AssetPosition) {
        requestedPosition = newPosition
        detailsShownAt++
    }

    Dialog(
        onDismissRequest = { onDismiss(position) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        NoWindowShadow()
        val focusRequester = remember { FocusRequester() }
        var controlsFocused by remember { mutableStateOf(false) }
        // Set by down on a video until the controls have taken focus.
        var focusControls by remember { mutableStateOf(false) }
        var lastSeekAt by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        // Take focus back when the video controls go away, so the keys keep working.
        LaunchedEffect(isVideo, showDetails) {
            if (!isVideo || !showDetails) focusRequester.requestFocus()
        }
        BackHandler(enabled = controlsFocused) { showDetails = false }
        KeepScreenOn(isVideo && !playPause.showPlay)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    // Keep the controls up while they're being used, but not when back is hiding them.
                    if (controlsFocused && event.type == KeyEventType.KeyDown && event.key != Key.Back) {
                        detailsShownAt++
                    }
                    false
                }
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val action = viewerActionFor(event.key, isVideo, controlsFocused) ?: return@onKeyEvent false
                    when (action) {
                        ViewerAction.Previous -> previous?.let(::show)
                        ViewerAction.Next -> next?.let(::show)
                        ViewerAction.ToggleDetails -> if (showDetails) showDetails = false else detailsShownAt++
                        ViewerAction.SeekBack, ViewerAction.SeekForward -> {
                            val keyEvent = event.nativeKeyEvent
                            // Holding the key skips at a steady pace rather than at the key repeat rate.
                            if (keyEvent.repeatCount == 0 || keyEvent.eventTime - lastSeekAt >= SeekRepeatMillis) {
                                lastSeekAt = keyEvent.eventTime
                                if (action == ViewerAction.SeekBack) player.seekBack() else player.seekForward()
                            }
                            detailsShownAt++
                        }
                        ViewerAction.PlayPause -> {
                            playPause.onClick()
                            detailsShownAt++
                        }
                        ViewerAction.Play -> {
                            Util.handlePlayButtonAction(player)
                            detailsShownAt++
                        }
                        ViewerAction.Pause -> {
                            Util.handlePauseButtonAction(player)
                            detailsShownAt++
                        }
                        ViewerAction.FocusControls -> {
                            focusControls = true
                            detailsShownAt++
                        }
                        ViewerAction.LeaveControls -> focusRequester.requestFocus()
                    }
                    true
                }
                .focusable()
        ) {
            when {
                asset == null -> CircularProgressIndicator()
                asset.isImage -> ViewerImage(asset, thumbnailUrl(asset.id), previewUrl(asset.id))
                else -> VideoSurface(player) { ViewerImage(asset, thumbnailUrl(asset.id), previewUrl(asset.id)) }
            }

            AnimatedVisibility(
                visible = showDetails && asset != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                if (asset != null) {
                    AssetDetails(
                        asset = asset,
                        overallIndex = layout.overallIndexOf(position),
                        total = layout.assetCount
                    ) {
                        if (!asset.isImage) {
                            VideoControls(
                                player = player,
                                knownDuration = asset.duration,
                                hasPrevious = previous != null,
                                hasNext = next != null,
                                onPrevious = { previous?.let(::show) },
                                onNext = { next?.let(::show) },
                                requestFocus = focusControls,
                                onFocusRequested = { focusControls = false },
                                modifier = Modifier
                                    .padding(top = 24.dp)
                                    .onFocusChanged { controlsFocused = it.hasFocus }
                            )
                            // Removing the focused controls doesn't always report the focus leaving.
                            DisposableEffect(Unit) { onDispose { controlsFocused = false } }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Removes the dialog window's shadow, which the full-screen viewer never shows. Android draws a
 * window with a shadow into a larger buffer and has the display hardware crop the margin off.
 * Some TVs (e.g. a Realtek-based Sanyo on Android 9) sometimes skip that crop while a video is on
 * screen, drawing the whole viewer shifted down and to the right.
 */
@Composable
private fun NoWindowShadow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    LaunchedEffect(window) { window?.setElevation(0f) }
}

/** Stops the screen saver from starting while [enabled], e.g. while a video plays. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun ViewerImage(asset: TimelineAsset, thumbnailUrl: String?, previewUrl: String?) {
    val context = LocalPlatformContext.current
    val request = remember(asset.id, previewUrl) {
        ImageRequest.Builder(context)
            .data(previewUrl)
            // Show the grid's thumbnail, or failing that the blurry placeholder, until the preview arrives.
            .placeholderMemoryCacheKey(thumbnailUrl)
            .placeholder(thumbHashBitmap(asset.thumbhash)?.asImage())
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
    )
}

/** Starts fetching the photos either side, so moving left or right shows them straight away. */
@Composable
private fun PreloadNeighbours(
    layout: TimelineLayout,
    position: AssetPosition,
    previewUrl: (assetId: String) -> String?,
    onLoadMonth: (month: Int) -> Unit
) {
    val context = LocalPlatformContext.current
    val neighbours = listOfNotNull(layout.positionAfter(position), layout.positionBefore(position))
    val neighbourIds = neighbours.map { layout.assetAt(it)?.id }
    LaunchedEffect(neighbourIds) {
        val imageLoader = SingletonImageLoader.get(context)
        neighbours.forEach { neighbour ->
            val asset = layout.assetAt(neighbour)
            if (asset == null) {
                onLoadMonth(neighbour.month)
            } else {
                previewUrl(asset.id)?.let { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
            }
        }
    }
}

/** The date, place and position of [asset], above anything in [content], e.g. video controls. */
@Composable
private fun AssetDetails(
    asset: TimelineAsset,
    overallIndex: Int,
    total: Int,
    content: @Composable () -> Unit = {}
) {
    val formats = rememberTimelineDateFormats()
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val place = listOfNotNull(asset.city, asset.country).joinToString(", ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formats.fullDate(asset.takenAt),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = if (place.isEmpty()) formats.time(asset.takenAt) else "${formats.time(asset.takenAt)} · $place",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Text(
                text = stringResource(
                    R.string.viewer_position,
                    numberFormat.format(overallIndex + 1),
                    numberFormat.format(total)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        content()
    }
}

private const val DetailsVisibleMillis = 4000L
private const val SeekRepeatMillis = 250L

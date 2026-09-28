package com.jagapathi.immichtv.ui.timeline

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.listenTo
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberErrorState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jagapathi.immichtv.R
import com.jagapathi.immichtv.ui.video.VideoSurfaceView
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows what [player] is playing, fitted to the screen. [placeholder] covers it until the first
 * frame is ready, with a spinner while it loads and a message if it can't be played.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoSurface(player: Player, placeholder: @Composable () -> Unit) {
    val playbackState = rememberPlaybackState(player)
    val error = rememberErrorState(player).error
    val presentation = rememberPresentationState(player)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        VideoSurfaceView(player, Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp))
        if (presentation.coverSurface) placeholder()
        when {
            error != null -> VideoError(error)
            playbackState == Player.STATE_BUFFERING -> CircularProgressIndicator()
        }
    }
}

@Composable
private fun VideoError(error: PlaybackException) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.video_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Text(
            text = stringResource(videoErrorMessage(error.errorCode)),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = stringResource(R.string.video_retry_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * A progress bar between the time played and the video's length, and buttons to play or pause
 * and to move to the previous or next item. [knownDuration] is shown until the player has read
 * the video's own. [requestFocus] focuses the play button, for when down is pressed to get here.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoControls(
    player: Player,
    knownDuration: Duration?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playPause = rememberPlayPauseButtonState(player)
    val isEnded = rememberPlaybackState(player) == Player.STATE_ENDED
    val time = rememberProgressStateWithTickInterval(player, tickIntervalMs = 1000)
    val duration = time.durationMs.takeIf { it != C.TIME_UNSET }?.milliseconds ?: knownDuration

    val playFocusRequester = remember { FocusRequester() }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            playFocusRequester.requestFocus()
            onFocusRequested()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimeLabel(formatDuration(time.currentPositionMs.milliseconds))
            ProgressBar(player, modifier = Modifier.weight(1f))
            TimeLabel(duration?.let(::formatDuration).orEmpty())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val (playIcon, playLabel) = when {
                isEnded -> R.drawable.ic_replay to R.string.play_again
                playPause.showPlay -> R.drawable.ic_play_arrow to R.string.play
                else -> R.drawable.ic_pause to R.string.pause
            }
            ControlButton(R.drawable.ic_skip_previous, R.string.previous, onClick = onPrevious, enabled = hasPrevious)
            ControlButton(
                playIcon,
                playLabel,
                onClick = playPause::onClick,
                modifier = Modifier.focusRequester(playFocusRequester)
            )
            ControlButton(R.drawable.ic_skip_next, R.string.next, onClick = onNext, enabled = hasNext)
        }
    }
}

@Composable
private fun TimeLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
        color = Color.White.copy(alpha = 0.8f)
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun ProgressBar(player: Player, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableIntStateOf(0) }
    // Moves on a pixel at a time, however long the video.
    val progress = rememberProgressStateWithTickCount(player, totalTickCount = widthPx)

    Canvas(
        modifier = modifier
            .height(12.dp)
            .onSizeChanged { widthPx = it.width }
    ) {
        val trackHeight = 4.dp.toPx()
        val top = (size.height - trackHeight) / 2
        val corner = CornerRadius(trackHeight / 2)
        fun bar(fraction: Float, color: Color) = drawRoundRect(
            color = color,
            topLeft = Offset(0f, top),
            size = size.copy(width = size.width * fraction.coerceIn(0f, 1f), height = trackHeight),
            cornerRadius = corner
        )
        bar(1f, Color.White.copy(alpha = 0.25f))
        bar(progress.bufferedPositionProgress, Color.White.copy(alpha = 0.4f))
        bar(progress.currentPositionProgress, Color.White)
        drawCircle(
            color = Color.White,
            radius = size.height / 2,
            center = Offset(size.width * progress.currentPositionProgress.coerceIn(0f, 1f), size.height / 2)
        )
    }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.15f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            disabledContainerColor = Color.White.copy(alpha = 0.05f),
            disabledContentColor = Color.White.copy(alpha = 0.35f)
        ),
        modifier = modifier
    ) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}

/** The player's [Player.getPlaybackState], kept up to date. */
@OptIn(UnstableApi::class)
@Composable
private fun rememberPlaybackState(player: Player): Int {
    var state by remember(player) { mutableIntStateOf(player.playbackState) }
    LaunchedEffect(player) {
        state = player.playbackState
        player.listenTo(Player.EVENT_PLAYBACK_STATE_CHANGED) { state = playbackState }
    }
    return state
}

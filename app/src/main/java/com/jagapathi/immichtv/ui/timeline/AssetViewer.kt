package com.jagapathi.immichtv.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
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
 * Shows one asset at a time over the whole screen. Left and right move through the timeline,
 * OK, up or down toggle the details, and back closes it, reporting where the viewer ended up so
 * the grid can focus that photo.
 */
@Composable
internal fun AssetViewer(
    layout: TimelineLayout,
    startPosition: AssetPosition,
    thumbnailUrl: (assetId: String) -> String?,
    previewUrl: (assetId: String) -> String?,
    onLoadMonth: (month: Int) -> Unit,
    onDismiss: (lastPosition: AssetPosition) -> Unit
) {
    var requestedPosition by remember { mutableStateOf(startPosition) }
    // The month may turn out smaller than the server's count once it loads.
    val position = layout.coerce(requestedPosition)
    val asset = layout.assetAt(position)

    // Bumped to show the details again and restart the timer that hides them.
    var detailsShownAt by remember { mutableIntStateOf(0) }
    var showDetails by remember { mutableStateOf(true) }
    LaunchedEffect(detailsShownAt) {
        showDetails = true
        delay(DetailsVisibleMillis)
        showDetails = false
    }

    LaunchedEffect(position.month, asset == null) {
        if (asset == null) onLoadMonth(position.month)
    }
    PreloadNeighbours(layout, position, previewUrl, onLoadMonth)

    Dialog(
        onDismissRequest = { onDismiss(position) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.MediaNext -> {
                            layout.positionAfter(position)?.let { requestedPosition = it }
                            detailsShownAt++
                            true
                        }
                        Key.DirectionLeft, Key.MediaPrevious -> {
                            layout.positionBefore(position)?.let { requestedPosition = it }
                            detailsShownAt++
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (showDetails) showDetails = false else detailsShownAt++
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
        ) {
            if (asset == null) {
                CircularProgressIndicator()
            } else {
                ViewerImage(asset, thumbnailUrl(asset.id), previewUrl(asset.id))
                if (!asset.isImage) VideoNotice()
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
                    )
                }
            }
        }
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

@Composable
private fun VideoNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play_arrow),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = stringResource(R.string.video_playback_unavailable),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

@Composable
private fun AssetDetails(asset: TimelineAsset, overallIndex: Int, total: Int) {
    val formats = rememberTimelineDateFormats()
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    val place = listOfNotNull(asset.city, asset.country).joinToString(", ")

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
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
}

private const val DetailsVisibleMillis = 4000L

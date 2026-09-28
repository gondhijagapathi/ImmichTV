package com.jagapathi.immichtv.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.jagapathi.immichtv.R

/**
 * What tiles show over videos and favorites: a shade at the top and bottom, a video's length with a
 * play icon, and a heart. Made once for the whole grid and drawn straight onto each tile (see
 * [assetBadges]). As composables of their own they made each tile several layout nodes, and
 * composing and placing those while scrolling was too slow on TVs.
 */
@Stable
internal class TileBadges(
    val play: Painter,
    val favorite: Painter,
    val textMeasurer: TextMeasurer,
    val textStyle: TextStyle
)

@Composable
internal fun rememberTileBadges(): TileBadges {
    val play = painterResource(R.drawable.ic_play_arrow)
    val favorite = painterResource(R.drawable.ic_favorite)
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall
    return remember(play, favorite, textMeasurer, textStyle) {
        TileBadges(play, favorite, textMeasurer, textStyle.copy(color = Color.White))
    }
}

/** Draws [asset]'s badges, if it has any, over the tile's content. */
internal fun Modifier.assetBadges(asset: TimelineAsset, badges: TileBadges): Modifier {
    val duration = asset.duration
    if (duration == null && !asset.isFavorite) return this
    return drawWithCache {
        val padding = BadgePadding.toPx()
        val iconSize = Size(BadgeIconSize.toPx(), BadgeIconSize.toPx())
        val durationText = duration?.let { badges.textMeasurer.measure(formatDuration(it), badges.textStyle) }
        onDrawWithContent {
            drawContent()
            // Keeps the white badges readable on bright photos.
            drawRect(BadgeShade)
            if (durationText != null) {
                // The length then the play icon, centred on one line in the top right corner.
                val lineHeight = maxOf(durationText.size.height.toFloat(), iconSize.height)
                val iconLeft = size.width - padding - iconSize.width
                drawText(
                    durationText,
                    topLeft = Offset(
                        iconLeft - durationText.size.width,
                        padding + (lineHeight - durationText.size.height) / 2
                    )
                )
                drawIcon(badges.play, Offset(iconLeft, padding + (lineHeight - iconSize.height) / 2), iconSize)
            }
            if (asset.isFavorite) {
                drawIcon(badges.favorite, Offset(padding, size.height - padding - iconSize.height), iconSize)
            }
        }
    }
}

private fun DrawScope.drawIcon(icon: Painter, topLeft: Offset, size: Size) {
    translate(topLeft.x, topLeft.y) {
        with(icon) { draw(size, colorFilter = BadgeTint) }
    }
}

private val BadgePadding = 6.dp
private val BadgeIconSize = 16.dp
private val BadgeTint = ColorFilter.tint(Color.White)
private val BadgeShade = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.35f),
    0.3f to Color.Transparent,
    0.7f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.35f)
)

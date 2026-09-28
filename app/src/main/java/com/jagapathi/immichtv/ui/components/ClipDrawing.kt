package com.jagapathi.immichtv.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath

/**
 * Clips what the modifiers after it draw to [shape], like `Modifier.clip` but without a graphics
 * layer. Up to Android 9, each graphics layer is backed by a View and setting its outline is slow,
 * so clipping every cell of a large grid with a layer made scrolling slow.
 */
internal fun Modifier.clipDrawing(shape: Shape): Modifier = drawWithCache {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
    onDrawWithContent {
        clipPath(path) { this@onDrawWithContent.drawContent() }
    }
}

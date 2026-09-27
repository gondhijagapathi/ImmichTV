package com.jagapathi.immichtv.ui.timeline

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The focus look of a clickable TV Material Surface, enlarged and outlined while focused, for
 * grids with thousands of cells. Unlike `androidx.tv.material3.Surface` it never recomposes:
 * moving focus only updates the cell's layer and redraws its outline, so scrolling through a grid
 * by holding a D-pad button stays cheap.
 *
 * Goes before the focusable (e.g. `clickable`) whose focus it shows, and outside any clip so the
 * outline can straddle the edge like the TV Material one.
 */
internal fun Modifier.focusIndication(focusedScale: Float, border: BorderStroke, shape: Shape): Modifier =
    this then FocusScaleElement(focusedScale) then FocusBorderElement(border, shape)

private data class FocusScaleElement(val focusedScale: Float) : ModifierNodeElement<FocusScaleNode>() {
    override fun create() = FocusScaleNode(focusedScale)

    override fun update(node: FocusScaleNode) {
        node.focusedScale = focusedScale
    }
}

private class FocusScaleNode(var focusedScale: Float) : Modifier.Node(), FocusEventModifierNode, LayoutModifierNode {
    private var isFocused = false
    private var scale by mutableFloatStateOf(1f)
    private var animation: Job? = null

    // Reads scale in the layer, so animating it only updates the layer.
    private val layerBlock: GraphicsLayerScope.() -> Unit = {
        scaleX = scale
        scaleY = scale
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == isFocused) return
        isFocused = focusState.isFocused
        val target = if (isFocused) focusedScale else 1f
        animation?.cancel()
        // Focus can also change as the cell leaves the grid.
        if (!isAttached) {
            scale = target
            return
        }
        // The z-index changes, so the enlarged cell is drawn over its neighbours.
        invalidatePlacement()
        animation = coroutineScope.launch {
            animate(scale, target, animationSpec = if (isFocused) FocusSpec else UnfocusSpec) { value, _ -> scale = value }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, zIndex = if (isFocused) 1f else 0f, layerBlock = layerBlock)
        }
    }

    // Lazy grids reuse cells for other items.
    override fun onReset() {
        animation?.cancel()
        isFocused = false
        scale = 1f
    }
}

private data class FocusBorderElement(val border: BorderStroke, val shape: Shape) : ModifierNodeElement<FocusBorderNode>() {
    override fun create() = FocusBorderNode(border, shape)

    override fun update(node: FocusBorderNode) {
        node.border = border
        node.shape = shape
        if (node.isAttached) node.invalidateDraw()
    }
}

private class FocusBorderNode(var border: BorderStroke, var shape: Shape) : Modifier.Node(), FocusEventModifierNode, DrawModifierNode {
    private var isFocused = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == isFocused) return
        isFocused = focusState.isFocused
        if (isAttached) invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (isFocused) {
            drawOutline(shape.createOutline(size, layoutDirection, this), border.brush, style = Stroke(border.width.toPx()))
        }
    }

    override fun onReset() {
        isFocused = false
    }
}

// The TV Material Surface timings.
private val FocusEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private val FocusSpec = tween<Float>(durationMillis = 300, easing = FocusEasing)
private val UnfocusSpec = tween<Float>(durationMillis = 500, easing = FocusEasing)

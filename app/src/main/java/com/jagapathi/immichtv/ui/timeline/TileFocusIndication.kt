package com.jagapathi.immichtv.ui.timeline

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
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
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The focus look of a clickable TV Material Surface, enlarged and outlined while focused, for
 * grids with thousands of cells. Unlike `androidx.tv.material3.Surface` it never recomposes: moving
 * focus only redraws the cell, so scrolling through a grid by holding a D-pad button stays cheap.
 *
 * The cell grows by [focusedGrowth] on each side rather than by a set factor, so that it can be
 * kept within the gap between cells whatever their size. The outline straddles the enlarged edge,
 * so it reaches half its width further.
 *
 * The cell is scaled as it's drawn rather than in a graphics layer of its own. Up to Android 9,
 * each graphics layer is backed by a View, and a layer for every cell made scrolling slow.
 *
 * Goes before the focusable (e.g. `clickable`) whose focus it shows, and outside any clip so the
 * outline can straddle the edge like the TV Material one.
 */
internal fun Modifier.focusIndication(focusedGrowth: Dp, border: BorderStroke, shape: Shape): Modifier =
    this then FocusGrowthElement(focusedGrowth) then FocusBorderElement(border, shape)

private data class FocusGrowthElement(val focusedGrowth: Dp) : ModifierNodeElement<FocusGrowthNode>() {
    override fun create() = FocusGrowthNode(focusedGrowth)

    override fun update(node: FocusGrowthNode) {
        node.focusedGrowth = focusedGrowth
    }
}

private class FocusGrowthNode(var focusedGrowth: Dp) :
    Modifier.Node(), FocusEventModifierNode, LayoutModifierNode, DrawModifierNode {
    private var isFocused = false
    // From 0 at rest to 1 when fully grown.
    private var growth = 0f
    private var animation: Job? = null

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == isFocused) return
        isFocused = focusState.isFocused
        val target = if (isFocused) 1f else 0f
        animation?.cancel()
        // Focus can also change as the cell leaves the grid.
        if (!isAttached) {
            growth = target
            return
        }
        // The z-index changes, so the enlarged cell is drawn over its neighbours.
        invalidatePlacement()
        animation = coroutineScope.launch {
            animate(growth, target, animationSpec = if (isFocused) FocusSpec else UnfocusSpec) { value, _ ->
                growth = value
                invalidateDraw()
            }
        }
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0, zIndex = if (isFocused) 1f else 0f)
        }
    }

    override fun ContentDrawScope.draw() {
        val extent = size.maxDimension
        if (growth == 0f || extent == 0f) {
            drawContent()
            return
        }
        scale(1f + growth * 2 * focusedGrowth.toPx() / extent) { this@draw.drawContent() }
    }

    // Lazy grids reuse cells for other items.
    override fun onReset() {
        animation?.cancel()
        isFocused = false
        growth = 0f
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

package com.jagapathi.immichtv.ui.timeline

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Moves focus up and down the timeline by column, like the Google TV and Immich web grids. Each day
 * starts a new row, so rows can be short: moving past a day with fewer photos than the column
 * focuses its last photo, and the next row that's long enough goes back to the column. Compose's
 * own focus search goes to the nearest photo instead, so focus stayed in the short row's column.
 *
 * The column is kept only while moving up and down. Focusing a photo any other way, e.g. moving
 * left or right, or closing the viewer, starts again from that photo's column.
 */
internal class ColumnFocus(private val columns: Int, private val layout: () -> TimelineLayout) {
    private val tiles = HashMap<AssetPosition, FocusRequesterModifierNode>()
    private var column: Int? = null
    private var isMovingVertically = false

    /**
     * Moves focus to the row above or below the photo at [from]. Returns false to leave the move to
     * Compose's own focus search, e.g. up out of the grid.
     */
    fun move(from: AssetPosition, down: Boolean): Boolean {
        val layout = layout()
        val column = column ?: layout.columnOf(from, columns)
        val target = if (down) layout.positionBelow(from, column, columns) else layout.positionAbove(from, column, columns)
        if (target == null) {
            // Leaving the grid, e.g. up to the tabs.
            this.column = null
            return false
        }
        this.column = column
        isMovingVertically = true
        // Rows just off screen are already composed, and the grid scrolls to the photo once it's
        // focused. Rows further away are left to Compose's search, which may pick another photo in
        // the row, but the column is still kept.
        return tiles[target]?.requestFocus() ?: false
    }

    /** Called when a photo gains focus. */
    fun onFocused() {
        if (!isMovingVertically) column = null
        isMovingVertically = false
    }

    /** Called for other keys, e.g. Left, so that focus they move doesn't count as moving up or down. */
    fun onOtherKey() {
        isMovingVertically = false
    }

    fun add(position: AssetPosition, tile: FocusRequesterModifierNode) {
        tiles[position] = tile
    }

    fun remove(position: AssetPosition, tile: FocusRequesterModifierNode) {
        tiles.remove(position, tile)
    }
}

/**
 * Lets [columnFocus] handle Up and Down on the photo at [position] and move focus to it. Goes before
 * the photo's focusable (e.g. `clickable`).
 */
internal fun Modifier.columnFocus(position: AssetPosition, columnFocus: ColumnFocus): Modifier =
    this then ColumnFocusElement(position, columnFocus)

private data class ColumnFocusElement(
    val position: AssetPosition,
    val columnFocus: ColumnFocus
) : ModifierNodeElement<ColumnFocusNode>() {
    override fun create() = ColumnFocusNode(position, columnFocus)

    override fun update(node: ColumnFocusNode) {
        node.update(position, columnFocus)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "columnFocus"
        properties["position"] = position
    }
}

private class ColumnFocusNode(
    private var position: AssetPosition,
    private var columnFocus: ColumnFocus
) : Modifier.Node(), FocusEventModifierNode, FocusRequesterModifierNode, KeyInputModifierNode {
    private var isFocused = false

    fun update(position: AssetPosition, columnFocus: ColumnFocus) {
        if (position == this.position && columnFocus === this.columnFocus) return
        if (isAttached) this.columnFocus.remove(this.position, this)
        this.position = position
        this.columnFocus = columnFocus
        if (isAttached) columnFocus.add(position, this)
    }

    override fun onAttach() {
        columnFocus.add(position, this)
    }

    override fun onDetach() {
        columnFocus.remove(position, this)
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == isFocused) return
        isFocused = focusState.isFocused
        if (isFocused) columnFocus.onFocused()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> columnFocus.move(position, down = true)
            Key.DirectionUp -> columnFocus.move(position, down = false)
            else -> {
                columnFocus.onOtherKey()
                false
            }
        }
    }

    override fun onPreKeyEvent(event: KeyEvent) = false

    // Lazy grids reuse cells for other photos.
    override fun onReset() {
        isFocused = false
    }
}

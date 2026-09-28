package com.jagapathi.immichtv.ui.components

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
 * Moves focus up and down a grid by column, like the Google TV and Immich web grids. Headers, such
 * as a timeline's days or the Albums tab's years, start new rows, so rows can be short: moving past
 * a shorter row focuses its last cell, and the next row that's long enough goes back to the column.
 * Compose's own focus search goes to the nearest cell instead, so focus stayed in the short row's
 * column. [rows] gives the grid's current rows, and cells are identified by [K].
 *
 * The column is kept only while moving up and down. Focusing a cell any other way, e.g. moving
 * left or right, or coming back from another screen, starts again from that cell's column.
 *
 * [isScrolling] tells whether the grid is scrolling, e.g. to bring the focused cell into view.
 */
internal class ColumnFocus<K : Any>(
    private val isScrolling: () -> Boolean,
    private val rows: () -> GridRows<K>
) {
    private val cells = HashMap<K, FocusRequesterModifierNode>()
    private var column: Int? = null
    private var isMovingVertically = false

    /**
     * Moves focus to the row above or below the cell [from]. Returns false to leave the move to
     * Compose's own focus search, e.g. up out of the grid.
     */
    fun move(from: K, down: Boolean): Boolean {
        val rows = rows()
        val column = column ?: rows.columnOf(from)
        // At the top or bottom. If Compose moves focus out of the grid, e.g. up to the tabs, the
        // column is reset when a cell is focused again.
        val target = (if (down) rows.below(from, column) else rows.above(from, column)) ?: return false
        val cell = cells[target]
        // Holding a button moves focus faster than the grid scrolls after it, so focus reaches rows
        // that aren't composed yet. Wait for the scroll to bring them in: Compose's own search
        // would lay the grid out past its edge a row at a time, which is far too slow to keep up.
        if (cell == null && isScrolling()) return true
        this.column = column
        isMovingVertically = true
        // Rows just off screen are already composed, and the grid scrolls to the cell once it's
        // focused. Rows further away are left to Compose's search, which may pick another cell in
        // the row, but the column is still kept.
        return cell?.requestFocus() ?: false
    }

    /** Called when a cell gains focus. */
    fun onFocused() {
        if (!isMovingVertically) column = null
        isMovingVertically = false
    }

    /** Called for other keys, e.g. Left, so that focus they move doesn't count as moving up or down. */
    fun onOtherKey() {
        isMovingVertically = false
    }

    fun add(key: K, cell: FocusRequesterModifierNode) {
        cells[key] = cell
    }

    fun remove(key: K, cell: FocusRequesterModifierNode) {
        cells.remove(key, cell)
    }
}

/**
 * Lets [columnFocus] handle Up and Down on the cell [key] and move focus to it. Goes before the
 * cell's focusable (e.g. `clickable`).
 */
internal fun <K : Any> Modifier.columnFocus(key: K, columnFocus: ColumnFocus<K>): Modifier =
    this then ColumnFocusElement(key, columnFocus)

private data class ColumnFocusElement<K : Any>(
    val key: K,
    val columnFocus: ColumnFocus<K>
) : ModifierNodeElement<ColumnFocusNode<K>>() {
    override fun create() = ColumnFocusNode(key, columnFocus)

    override fun update(node: ColumnFocusNode<K>) {
        node.update(key, columnFocus)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "columnFocus"
        properties["key"] = key
    }
}

private class ColumnFocusNode<K : Any>(
    private var key: K,
    private var columnFocus: ColumnFocus<K>
) : Modifier.Node(), FocusEventModifierNode, FocusRequesterModifierNode, KeyInputModifierNode {
    private var isFocused = false

    fun update(key: K, columnFocus: ColumnFocus<K>) {
        if (key == this.key && columnFocus === this.columnFocus) return
        if (isAttached) this.columnFocus.remove(this.key, this)
        this.key = key
        this.columnFocus = columnFocus
        if (isAttached) columnFocus.add(key, this)
    }

    override fun onAttach() {
        columnFocus.add(key, this)
    }

    override fun onDetach() {
        columnFocus.remove(key, this)
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused == isFocused) return
        isFocused = focusState.isFocused
        if (isFocused) columnFocus.onFocused()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionDown -> columnFocus.move(key, down = true)
            Key.DirectionUp -> columnFocus.move(key, down = false)
            else -> {
                columnFocus.onOtherKey()
                false
            }
        }
    }

    override fun onPreKeyEvent(event: KeyEvent) = false

    // Lazy grids reuse cells for other items.
    override fun onReset() {
        isFocused = false
    }
}

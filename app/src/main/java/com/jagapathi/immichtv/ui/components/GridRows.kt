package com.jagapathi.immichtv.ui.components

/**
 * Finds cells above and below each other in a lazy grid [columns] wide whose headers span its
 * width, so each header starts a new row: a timeline's days, or the Albums tab's years. Items are
 * counted by their index in the grid, headers included. [cellAt] gives the cell at an index, or
 * null for a header, and [indexOf] finds a cell's index.
 */
internal class GridRows<K : Any>(
    private val columns: Int,
    private val itemCount: Int,
    private val cellAt: (index: Int) -> K?,
    private val indexOf: (cell: K) -> Int?
) {
    /** The column of [cell]. The first cell after a header is in the first column. */
    fun columnOf(cell: K): Int {
        val index = indexOf(cell) ?: return 0
        return (index - runStart(index)) % columns
    }

    /** The cell in [column] of the row below [cell], or the row's last cell if it's shorter. Null on the last row. */
    fun below(cell: K, column: Int): K? {
        val index = indexOf(cell) ?: return null
        val rowEnd = index - (index - runStart(index)) % columns + columns
        // Past the rest of this row and any headers after it.
        var next = index + 1
        while (next < rowEnd && isCell(next)) next++
        while (next < itemCount && !isCell(next)) next++
        return if (next < itemCount) cellInRow(next, column) else null
    }

    /** The cell in [column] of the row above [cell], or the row's last cell if it's shorter. Null on the first row. */
    fun above(cell: K, column: Int): K? {
        val index = indexOf(cell) ?: return null
        val start = runStart(index)
        val rowStart = index - (index - start) % columns
        if (rowStart > start) return cellInRow(rowStart - columns, column)
        // The last row before the headers above.
        var last = start - 1
        while (last >= 0 && !isCell(last)) last--
        if (last < 0) return null
        return cellInRow(last - (last - runStart(last)) % columns, column)
    }

    private fun cellInRow(rowStart: Int, column: Int): K {
        var index = rowStart
        while (index < rowStart + column.coerceAtMost(columns - 1) && isCell(index + 1)) index++
        return checkNotNull(cellAt(index))
    }

    /** The first index of the run of cells between headers that [index] is in. */
    private fun runStart(index: Int): Int {
        var start = index
        while (start > 0 && isCell(start - 1)) start--
        return start
    }

    private fun isCell(index: Int) = index in 0 until itemCount && cellAt(index) != null
}

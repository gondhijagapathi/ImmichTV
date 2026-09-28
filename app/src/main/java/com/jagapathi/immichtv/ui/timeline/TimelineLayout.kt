package com.jagapathi.immichtv.ui.timeline

import com.jagapathi.immichtv.ui.components.GridRows
import java.time.LocalDate
import java.time.YearMonth

/** One month of a timeline. [assets] stays null until the month has been loaded. */
data class TimelineMonth(
    /** The server's id for the month, passed back when loading its assets. */
    val bucket: String,
    val yearMonth: YearMonth,
    val count: Int,
    val assets: List<TimelineAsset>? = null
) {
    /** How many tiles the month takes: the real number once loaded, the server's count until then. */
    val size: Int get() = assets?.size ?: count
}

/** The [index]-th asset of the [month]-th month. */
data class AssetPosition(val month: Int, val index: Int)

/** A cell of the timeline grid. */
sealed interface TimelineItem {
    val key: String

    data class MonthHeader(val yearMonth: YearMonth, override val key: String) : TimelineItem, TimelineRow

    data class DayHeader(val date: LocalDate, override val key: String) : TimelineItem, TimelineRow

    /** A photo, or a placeholder while its month loads ([asset] is null). */
    data class Tile(val position: AssetPosition, val asset: TimelineAsset?, override val key: String) : TimelineItem
}

/** A row of the timeline grid: a month's or a day's header, or a [TileRow]. */
sealed interface TimelineRow {
    val key: String
}

/** Up to a row's worth of one day's tiles, or of the placeholders of a month that hasn't loaded. */
data class TileRow(val tiles: List<TimelineItem.Tile>, override val key: String) : TimelineRow

/**
 * Lays the months out as a grid [columns] wide: a header per month, a header per day once the
 * month is loaded, and a tile per asset. Every header starts a new row, so a day's photos, or a
 * month's until it has loaded, start in the first column.
 *
 * The grid is listed both cell by cell ([items]) and row by row ([rows]). The timeline shows it a
 * row at a time, which is much cheaper to scroll than a lazy grid of single photos.
 *
 * Tile keys depend only on the asset's position, so a placeholder turns into its photo in place
 * when the month finishes loading.
 */
class TimelineLayout(val months: List<TimelineMonth>, val columns: Int) {
    val items: List<TimelineItem>
    val rows: List<TimelineRow>
    private val monthRows = IntArray(months.size)
    private val assetsBefore = IntArray(months.size)
    private val tileIndices: Array<IntArray>
    private val tileRows: Array<IntArray> = Array(months.size) { IntArray(months[it].size) }

    /** Number of assets on the whole timeline. */
    val assetCount: Int

    init {
        val cells = ArrayList<TimelineItem>(months.sumOf { it.size + 1 })
        val lines = ArrayList<TimelineRow>()
        val row = ArrayList<TimelineItem.Tile>(columns)
        fun endRow() {
            if (row.isEmpty()) return
            for (tile in row) tileRows[tile.position.month][tile.position.index] = lines.size
            lines += TileRow(row.toList(), "row:${row[0].key}")
            row.clear()
        }

        var total = 0
        tileIndices = Array(months.size) { m ->
            val month = months[m]
            monthRows[m] = lines.size
            assetsBefore[m] = total
            total += month.size
            val monthHeader = TimelineItem.MonthHeader(month.yearMonth, "month:${month.bucket}")
            cells += monthHeader
            lines += monthHeader

            val indices = IntArray(month.size)
            var day: LocalDate? = null
            for (i in 0 until month.size) {
                val asset = month.assets?.get(i)
                val assetDay = asset?.takenAt?.toLocalDate()
                if (assetDay != null && assetDay != day) {
                    day = assetDay
                    endRow()
                    val dayHeader = TimelineItem.DayHeader(assetDay, "day:${month.bucket}:$assetDay")
                    cells += dayHeader
                    lines += dayHeader
                }
                indices[i] = cells.size
                val tile = TimelineItem.Tile(AssetPosition(m, i), asset, tileKey(month, i))
                cells += tile
                row += tile
                if (row.size == columns) endRow()
            }
            endRow()
            indices
        }
        items = cells
        rows = lines
        assetCount = total
    }

    /** The row of the [month]-th month's header. */
    fun monthHeaderIndex(month: Int): Int = monthRows[month]

    /** The cell of the asset at [position], or null if there's no such asset (e.g. an empty month). */
    fun itemIndexOf(position: AssetPosition): Int? =
        tileIndices.getOrNull(position.month)?.getOrNull(position.index)

    /** The row of the asset at [position], or null if there's no such asset (e.g. an empty month). */
    fun rowIndexOf(position: AssetPosition): Int? =
        tileRows.getOrNull(position.month)?.getOrNull(position.index)

    fun tileKey(position: AssetPosition): String = tileKey(months[position.month], position.index)

    /** The month that the row at [rowIndex] belongs to. */
    fun monthAt(rowIndex: Int): Int {
        if (months.isEmpty()) return 0
        val found = monthRows.binarySearch(rowIndex.coerceAtLeast(0))
        return if (found >= 0) found else -found - 2
    }

    /** How far through its month the row at [rowIndex] is, from 0 to 1. */
    fun progressInMonth(rowIndex: Int): Float {
        val month = monthAt(rowIndex)
        val start = monthRows.getOrElse(month) { return 0f }
        val end = monthRows.getOrElse(month + 1) { rows.size }
        return ((rowIndex - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    fun assetAt(position: AssetPosition): TimelineAsset? =
        months.getOrNull(position.month)?.assets?.getOrNull(position.index)

    /** Keeps [position] on the timeline if its month turned out smaller than the server said. */
    fun coerce(position: AssetPosition): AssetPosition {
        val size = months.getOrNull(position.month)?.size ?: return position
        return if (position.index < size || size == 0) position else position.copy(index = size - 1)
    }

    /** The next (older) asset, or null at the end of the timeline. */
    fun positionAfter(position: AssetPosition): AssetPosition? {
        if (position.index + 1 < months[position.month].size) return position.copy(index = position.index + 1)
        val next = (position.month + 1 until months.size).firstOrNull { months[it].size > 0 } ?: return null
        return AssetPosition(next, 0)
    }

    /** The previous (newer) asset, or null at the start of the timeline. */
    fun positionBefore(position: AssetPosition): AssetPosition? {
        if (position.index > 0) return position.copy(index = position.index - 1)
        val previous = (position.month - 1 downTo 0).firstOrNull { months[it].size > 0 } ?: return null
        return AssetPosition(previous, months[previous].size - 1)
    }

    /** Where [position] falls on the whole timeline, counting from 0. */
    fun overallIndexOf(position: AssetPosition): Int = assetsBefore[position.month] + position.index

    /** Finds the tiles above and below each other, for moving focus up and down the grid. */
    internal fun gridRows(): GridRows<AssetPosition> =
        GridRows(columns, items.size, cellAt = { (items[it] as? TimelineItem.Tile)?.position }, indexOf = ::itemIndexOf)

    private fun tileKey(month: TimelineMonth, index: Int) = "asset:${month.bucket}:$index"
}

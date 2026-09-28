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

    data class MonthHeader(val yearMonth: YearMonth, override val key: String) : TimelineItem

    data class DayHeader(val date: LocalDate, override val key: String) : TimelineItem

    /** A photo, or a placeholder while its month loads ([asset] is null). */
    data class Tile(val position: AssetPosition, val asset: TimelineAsset?, override val key: String) : TimelineItem
}

/**
 * Lays the months out as grid cells: a header per month, a header per day once the month is
 * loaded, and a tile per asset. Tile keys depend only on the asset's position, so a placeholder
 * turns into its photo in place and keeps focus when the month finishes loading.
 */
class TimelineLayout(val months: List<TimelineMonth>) {
    val items: List<TimelineItem>
    private val monthStarts = IntArray(months.size)
    private val assetsBefore = IntArray(months.size)
    private val tileIndices: Array<IntArray>

    /** Number of assets on the whole timeline. */
    val assetCount: Int

    init {
        val cells = ArrayList<TimelineItem>(months.sumOf { it.size + 1 })
        var total = 0
        tileIndices = Array(months.size) { m ->
            val month = months[m]
            monthStarts[m] = cells.size
            assetsBefore[m] = total
            total += month.size
            cells += TimelineItem.MonthHeader(month.yearMonth, "month:${month.bucket}")

            val indices = IntArray(month.size)
            var day: LocalDate? = null
            for (i in 0 until month.size) {
                val asset = month.assets?.get(i)
                val assetDay = asset?.takenAt?.toLocalDate()
                if (assetDay != null && assetDay != day) {
                    day = assetDay
                    cells += TimelineItem.DayHeader(assetDay, "day:${month.bucket}:$assetDay")
                }
                indices[i] = cells.size
                cells += TimelineItem.Tile(AssetPosition(m, i), asset, tileKey(month, i))
            }
            indices
        }
        items = cells
        assetCount = total
    }

    fun monthHeaderIndex(month: Int): Int = monthStarts[month]

    /** The cell of the asset at [position], or null if there's no such asset (e.g. an empty month). */
    fun itemIndexOf(position: AssetPosition): Int? =
        tileIndices.getOrNull(position.month)?.getOrNull(position.index)

    fun tileKey(position: AssetPosition): String = tileKey(months[position.month], position.index)

    /** The month that the cell at [itemIndex] belongs to. */
    fun monthAt(itemIndex: Int): Int {
        if (months.isEmpty()) return 0
        val found = monthStarts.binarySearch(itemIndex.coerceAtLeast(0))
        return if (found >= 0) found else -found - 2
    }

    /** How far through its month the cell at [itemIndex] is, from 0 to 1. */
    fun progressInMonth(itemIndex: Int): Float {
        val month = monthAt(itemIndex)
        val start = monthStarts.getOrElse(month) { return 0f }
        val end = monthStarts.getOrElse(month + 1) { items.size }
        return ((itemIndex - start).toFloat() / (end - start)).coerceIn(0f, 1f)
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

    /**
     * The rows of this timeline in a grid [columns] wide. Every header starts a new row, so a day's
     * photos, or a month's until it has loaded, start in the first column.
     */
    internal fun rows(columns: Int): GridRows<AssetPosition> =
        GridRows(columns, items.size, cellAt = { (items[it] as? TimelineItem.Tile)?.position }, indexOf = ::itemIndexOf)

    private fun tileKey(month: TimelineMonth, index: Int) = "asset:${month.bucket}:$index"
}

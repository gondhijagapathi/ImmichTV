package com.jagapathi.immichtv.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TimelineLayoutTest {

    private fun asset(id: String, day: Int, month: Int = 5) = TimelineAsset(
        id = id,
        takenAt = LocalDateTime.of(2024, month, day, 12, 0),
        isImage = true,
        isFavorite = false,
        duration = null,
        thumbhash = null,
        city = null,
        country = null
    )

    private val may = TimelineMonth(
        bucket = "2024-05-01",
        yearMonth = YearMonth.of(2024, 5),
        count = 3,
        assets = listOf(asset("a", 20), asset("b", 20), asset("c", 3))
    )
    private val april = TimelineMonth(bucket = "2024-04-01", yearMonth = YearMonth.of(2024, 4), count = 2)

    @Test
    fun `groups loaded months by day and shows placeholders for the rest`() {
        val layout = TimelineLayout(listOf(may, april))

        val cells = layout.items.map {
            when (it) {
                is TimelineItem.MonthHeader -> "month ${it.yearMonth}"
                is TimelineItem.DayHeader -> "day ${it.date}"
                is TimelineItem.Tile -> "tile ${it.asset?.id ?: "?"}"
            }
        }
        assertEquals(
            listOf(
                "month 2024-05", "day 2024-05-20", "tile a", "tile b", "day 2024-05-03", "tile c",
                "month 2024-04", "tile ?", "tile ?"
            ),
            cells
        )
        assertEquals(5, layout.assetCount)
        assertEquals(layout.items.size, layout.items.map { it.key }.toSet().size)
    }

    @Test
    fun `a placeholder keeps its key once its month loads`() {
        val before = TimelineLayout(listOf(may, april))
        val loadedApril = april.copy(assets = listOf(asset("d", 9, 4), asset("e", 1, 4)))
        val after = TimelineLayout(listOf(may, loadedApril))

        val position = AssetPosition(1, 1)
        assertEquals(before.tileKey(position), after.tileKey(position))
        assertNull((before.items[before.itemIndexOf(position)!!] as TimelineItem.Tile).asset)
        assertEquals("e", (after.items[after.itemIndexOf(position)!!] as TimelineItem.Tile).asset?.id)
    }

    @Test
    fun `finds the month of any cell`() {
        val layout = TimelineLayout(listOf(may, april))

        assertEquals(0, layout.monthAt(-1))
        assertEquals(0, layout.monthAt(0))
        assertEquals(0, layout.monthAt(5))
        assertEquals(1, layout.monthAt(layout.monthHeaderIndex(1)))
        assertEquals(1, layout.monthAt(layout.items.lastIndex))
        assertEquals(1, layout.monthAt(1000))
        assertEquals(0f, layout.progressInMonth(0))
        assertTrue(layout.progressInMonth(4) in 0.5f..1f)
    }

    @Test
    fun `steps through assets across month boundaries`() {
        val layout = TimelineLayout(listOf(may, april))

        assertEquals(AssetPosition(1, 0), layout.positionAfter(AssetPosition(0, 2)))
        assertEquals(AssetPosition(0, 2), layout.positionBefore(AssetPosition(1, 0)))
        assertNull(layout.positionBefore(AssetPosition(0, 0)))
        assertNull(layout.positionAfter(AssetPosition(1, 1)))
        assertEquals(3, layout.overallIndexOf(AssetPosition(1, 0)))
        assertEquals(LocalDate.of(2024, 5, 3), layout.assetAt(AssetPosition(0, 2))?.takenAt?.toLocalDate())
    }

    @Test
    fun `skips months that came back empty`() {
        // e.g. everything in April was deleted between listing the months and loading them.
        val march = TimelineMonth("2024-03-01", YearMonth.of(2024, 3), 1, listOf(asset("f", 2, 3)))
        val layout = TimelineLayout(listOf(may, april.copy(assets = emptyList()), march))

        assertNull(layout.itemIndexOf(AssetPosition(1, 0)))
        assertEquals(AssetPosition(2, 0), layout.positionAfter(AssetPosition(0, 2)))
        assertEquals(AssetPosition(0, 2), layout.positionBefore(AssetPosition(2, 0)))
    }

    @Test
    fun `keeps positions inside months that turned out smaller`() {
        // The server counted two assets, but only one came back.
        val layout = TimelineLayout(listOf(may, april.copy(assets = listOf(asset("d", 9, 4)))))

        assertEquals(AssetPosition(1, 0), layout.coerce(AssetPosition(1, 1)))
        assertEquals(AssetPosition(0, 1), layout.coerce(AssetPosition(0, 1)))
    }
}

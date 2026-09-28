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

    // In a grid 3 wide: June's days take rows of 3, 2, 1, 3 and 1 photos, unloaded July's photos rows of
    // 3 and 1, and March's day a row of 2. The emptied April has no rows.
    private fun photos(month: Int, vararg days: Pair<Int, Int>) =
        days.flatMap { (day, count) -> List(count) { asset("$month-$day-$it", day, month) } }

    private val gridLayout = TimelineLayout(
        listOf(
            TimelineMonth("2024-07-01", YearMonth.of(2024, 7), 4),
            TimelineMonth("2024-06-01", YearMonth.of(2024, 6), 10, photos(6, 20 to 5, 18 to 1, 10 to 4)),
            TimelineMonth("2024-04-01", YearMonth.of(2024, 4), 1, emptyList()),
            TimelineMonth("2024-03-01", YearMonth.of(2024, 3), 2, photos(3, 2 to 2))
        )
    )

    private fun walk(from: AssetPosition, column: Int, step: TimelineLayout.(AssetPosition, Int, Int) -> AssetPosition?) =
        generateSequence(from) { gridLayout.step(it, column, 3) }.toList()

    @Test
    fun `finds the column of an asset when days start new rows`() {
        assertEquals(2, gridLayout.columnOf(AssetPosition(0, 2), 3))
        assertEquals(0, gridLayout.columnOf(AssetPosition(0, 3), 3))
        assertEquals(1, gridLayout.columnOf(AssetPosition(1, 4), 3))
        assertEquals(0, gridLayout.columnOf(AssetPosition(1, 5), 3))
        assertEquals(1, gridLayout.columnOf(AssetPosition(1, 7), 3))
        assertEquals(0, gridLayout.columnOf(AssetPosition(1, 9), 3))
    }

    @Test
    fun `moving down keeps the column past shorter rows`() {
        assertEquals(
            listOf(
                AssetPosition(0, 2), AssetPosition(0, 3), // July's second row has one photo.
                AssetPosition(1, 2), AssetPosition(1, 4), AssetPosition(1, 5), // June's rows of 3, 2 and 1.
                AssetPosition(1, 8), AssetPosition(1, 9),
                AssetPosition(3, 1) // Past the emptied April.
            ),
            walk(AssetPosition(0, 2), column = 2, step = TimelineLayout::positionBelow)
        )
    }

    @Test
    fun `moving up keeps the column past shorter rows`() {
        assertEquals(
            listOf(
                AssetPosition(3, 1),
                AssetPosition(1, 9), AssetPosition(1, 8), AssetPosition(1, 5), AssetPosition(1, 4), AssetPosition(1, 2),
                AssetPosition(0, 3), AssetPosition(0, 2)
            ),
            walk(AssetPosition(3, 1), column = 2, step = TimelineLayout::positionAbove)
        )
    }

    @Test
    fun `moves straight up and down in the first column`() {
        assertEquals(
            listOf(AssetPosition(0, 0), AssetPosition(0, 3), AssetPosition(1, 0), AssetPosition(1, 3)),
            walk(AssetPosition(0, 0), column = 0, step = TimelineLayout::positionBelow).take(4)
        )
        assertEquals(
            listOf(AssetPosition(1, 3), AssetPosition(1, 0), AssetPosition(0, 3), AssetPosition(0, 0)),
            walk(AssetPosition(1, 3), column = 0, step = TimelineLayout::positionAbove)
        )
    }
}

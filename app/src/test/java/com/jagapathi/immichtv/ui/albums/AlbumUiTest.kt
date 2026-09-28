package com.jagapathi.immichtv.ui.albums

import com.jagapathi.immichtv.model.ImmichAlbumDto
import com.jagapathi.immichtv.model.ImmichAlbumUserDto
import com.jagapathi.immichtv.model.ImmichUserDto
import com.jagapathi.immichtv.model.TimelineQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class AlbumUiTest {

    private val me = ImmichUserDto(id = "me", name = "Me", email = "me@example.com")
    private val asha = ImmichUserDto(id = "asha", name = "Asha", email = "asha@example.com")

    private fun dto(
        id: String = "album-1",
        name: String = "Album",
        owner: ImmichUserDto? = null,
        albumUsers: List<ImmichUserDto> = listOf(me),
        shared: Boolean = false,
        endDate: String? = null
    ) = ImmichAlbumDto(
        id = id,
        albumName = name,
        shared = shared,
        endDate = endDate?.let(Instant::parse),
        owner = owner,
        albumUsers = albumUsers.map(::ImmichAlbumUserDto)
    )

    private fun ImmichAlbumDto.toUi() = toAlbumUi(userId = "me") { "thumb/$it" }

    @Test
    fun `maps an album with its cover, local photo dates and order`() {
        val album = ImmichAlbumDto(
            id = "album-1",
            albumName = "Goa",
            albumThumbnailAssetId = "asset-1",
            assetCount = 12,
            description = "  Beach days  ",
            startDate = Instant.parse("2024-03-03T09:15:00.000Z"),
            endDate = Instant.parse("2024-03-18T23:30:00.000Z"),
            order = "asc",
            albumUsers = listOf(ImmichAlbumUserDto(me))
        ).toUi()

        assertEquals("Goa", album.name)
        assertEquals("Beach days", album.description)
        assertEquals("thumb/asset-1", album.coverUrl)
        assertEquals(12, album.assetCount)
        // The UTC timestamps are the local times the photos were taken.
        assertEquals(LocalDateTime.of(2024, 3, 3, 9, 15), album.oldestPhotoAt)
        assertEquals(LocalDateTime.of(2024, 3, 18, 23, 30), album.newestPhotoAt)
        assertEquals(TimelineQuery(albumId = "album-1", order = "asc"), album.timeline)
        assertFalse(album.isShared)
        assertNull(album.sharedBy)
    }

    @Test
    fun `an empty album has no cover, dates or description`() {
        val album = ImmichAlbumDto(id = "empty", albumName = "Empty", description = null).toUi()

        assertNull(album.coverUrl)
        assertNull(album.oldestPhotoAt)
        assertNull(album.newestPhotoAt)
        assertEquals("", album.description)
    }

    @Test
    fun `tells own albums from ones shared with the user on Immich v3`() {
        // v3 has no owner field; the owner is the first album user.
        val sharedWithMe = dto(albumUsers = listOf(asha, me), shared = true).toUi()
        val sharedByMe = dto(albumUsers = listOf(me, asha), shared = true).toUi()

        assertTrue(sharedWithMe.isShared)
        assertEquals("Asha", sharedWithMe.sharedBy)
        assertTrue(sharedByMe.isShared)
        assertNull(sharedByMe.sharedBy)
    }

    @Test
    fun `reads the owner field of servers before Immich v3`() {
        // Before v3, album users are only the people it's shared with, owner excluded.
        val sharedWithMe = dto(owner = asha, albumUsers = listOf(me), shared = true).toUi()
        val sharedByMe = dto(owner = me, albumUsers = listOf(asha), shared = true).toUi()

        assertEquals("Asha", sharedWithMe.sharedBy)
        assertNull(sharedByMe.sharedBy)
    }

    @Test
    fun `names the owner by email when they have no name`() {
        val noName = asha.copy(name = " ")
        val noNameOrEmail = asha.copy(name = null, email = null)

        assertEquals("asha@example.com", dto(albumUsers = listOf(noName, me), shared = true).toUi().sharedBy)
        val anonymous = dto(albumUsers = listOf(noNameOrEmail, me), shared = true).toUi()
        assertNull(anonymous.sharedBy)
        assertTrue(anonymous.isShared)
    }

    @Test
    fun `groups albums by the year of their newest photo, newest first, empty albums last`() {
        val albums = listOf(
            dto(id = "empty-b", name = "b"),
            dto(id = "2023", endDate = "2023-12-31T23:00:00Z"),
            dto(id = "2024-jan", endDate = "2024-01-05T10:00:00Z"),
            dto(id = "empty-a", name = "A"),
            dto(id = "2024-dec", endDate = "2024-12-01T10:00:00Z"),
            // Same newest photo as 2024-jan: ordered by name.
            dto(id = "2024-jan-a", name = "Aardvark", endDate = "2024-01-05T10:00:00Z")
        ).map { it.toUi() }

        val groups = groupByYear(albums)

        assertEquals(listOf(2024, 2023, null), groups.map { it.year })
        assertEquals(
            listOf(listOf("2024-dec", "2024-jan-a", "2024-jan"), listOf("2023"), listOf("empty-a", "empty-b")),
            groups.map { group -> group.albums.map { it.id } }
        )
    }

    @Test
    fun `no albums means no groups`() {
        assertTrue(groupByYear(emptyList()).isEmpty())
    }

    @Test
    fun `moving up and down the albums keeps the column past shorter years`() {
        // In a grid 3 wide: 2024 takes a row of 2, 2023 rows of 3 and 1, and the empty albums a row of 3.
        fun albums(vararg ids: String) = ids.map { dto(id = it).toUi() }
        val rows = albumRows(
            listOf(
                AlbumGroup(2024, albums("a", "b")),
                AlbumGroup(2023, albums("c", "d", "e", "f")),
                AlbumGroup(null, albums("g", "h", "i"))
            ),
            columns = 3
        )

        assertEquals(2, rows.columnOf("e"))
        assertEquals(listOf("e", "f", "i"), generateSequence("e") { rows.below(it, 2) }.toList())
        assertEquals(listOf("i", "f", "e", "b"), generateSequence("i") { rows.above(it, 2) }.toList())
        assertEquals(listOf("a", "c", "f", "g"), generateSequence("a") { rows.below(it, 0) }.toList())
    }
}

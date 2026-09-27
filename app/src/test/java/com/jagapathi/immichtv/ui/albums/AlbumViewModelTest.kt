package com.jagapathi.immichtv.ui.albums

import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.testing.FakeImmich
import com.jagapathi.immichtv.testing.await
import com.jagapathi.immichtv.testing.respondJson
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private lateinit var immich: FakeImmich

    private fun viewModel(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): AlbumViewModel {
        immich = FakeImmich(handler)
        return AlbumViewModel("album-1", immich.apiService, immich.repository)
    }

    private suspend fun AlbumViewModel.awaitError() = state.await { it is AlbumUiState.Error } as AlbumUiState.Error

    @Test
    fun `loads the album with the timeline to show`() = runBlocking {
        val viewModel = viewModel {
            respondJson(
                """{"id": "album-1", "albumName": "Tokyo", "assetCount": 2, "order": "asc", "shared": false,
                    "albumUsers": [{"user": {"id": "user-1", "name": "User"}, "role": "owner"}]}"""
            )
        }

        val album = (viewModel.state.await { it is AlbumUiState.Ready } as AlbumUiState.Ready).album

        assertEquals("/api/albums/album-1", immich.requests.single().url.encodedPath)
        assertEquals("Tokyo", album.name)
        assertEquals(TimelineQuery(albumId = "album-1", order = "asc"), album.timeline)
    }

    @Test
    fun `an album that's gone can't be retried`() = runBlocking {
        // What Immich answers for albums that were deleted or unshared.
        val viewModel = viewModel {
            respondJson("""{"message": "Not found or no album.read access"}""", HttpStatusCode.BadRequest)
        }

        val error = viewModel.awaitError()

        assertTrue(error.isGone)
        assertEquals("This album isn't available anymore. It may have been deleted or unshared.", error.message)
    }

    @Test
    fun `other failures can be retried`() = runBlocking<Unit> {
        var fail = true
        val viewModel = viewModel {
            if (fail) {
                respondJson("{}", HttpStatusCode.ServiceUnavailable)
            } else {
                respondJson("""{"id": "album-1", "albumName": "Tokyo"}""")
            }
        }

        val error = viewModel.awaitError()
        assertFalse(error.isGone)
        assertEquals(
            "Couldn't load this album. The Immich server had an error (HTTP 503). Try again later.",
            error.message
        )

        fail = false
        viewModel.retry()
        viewModel.state.await { it is AlbumUiState.Ready }
    }
}

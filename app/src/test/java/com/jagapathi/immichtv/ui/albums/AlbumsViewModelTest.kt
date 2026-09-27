package com.jagapathi.immichtv.ui.albums

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        FakeImmich(handler).let { AlbumsViewModel(it.apiService, it.repository) }

    private fun album(id: String, endDate: String?, owner: String = "user-1") = """
        {"id": "$id", "albumName": "Album $id", "albumThumbnailAssetId": "cover-$id", "assetCount": 3,
         "description": "", "shared": ${owner != "user-1"}, "hasSharedLink": false, "isActivityEnabled": true,
         "createdAt": "2024-01-01T00:00:00.000Z", "updatedAt": "2024-01-01T00:00:00.000Z",
         ${endDate?.let { "\"endDate\": \"$it\"," } ?: ""}
         "albumUsers": [{"user": {"id": "$owner", "name": "Name of $owner", "email": "$owner@example.com"},
                         "role": "owner"}]}
    """

    @Test
    fun `lists own and shared albums grouped by year`() = runBlocking {
        val viewModel = viewModel { request ->
            // An older server: own albums by default, shared ones when asked.
            if (request.url.parameters["shared"] == "true") {
                respondJson("[${album("shared", "2023-06-01T10:00:00.000Z", owner = "asha")}]")
            } else {
                respondJson("[${album("mine", "2024-06-01T10:00:00.000Z")}, ${album("empty", null)}]")
            }
        }

        val groups = (viewModel.state.await { it is AlbumsUiState.Ready } as AlbumsUiState.Ready).groups

        assertEquals(listOf(2024, 2023, null), groups.map { it.year })
        assertEquals(listOf("mine", "shared", "empty"), groups.flatMap { group -> group.albums.map { it.id } })
        val shared = groups[1].albums.single()
        assertEquals("Name of asha", shared.sharedBy)
        assertEquals("http://immich.local/api/assets/cover-shared/thumbnail?size=thumbnail", shared.coverUrl)
    }

    @Test
    fun `a failed load shows an error until a retry succeeds`() = runBlocking {
        var fail = true
        val viewModel = viewModel {
            if (fail) respondJson("{}", HttpStatusCode.InternalServerError) else respondJson("[]")
        }

        val error = viewModel.state.await { it is AlbumsUiState.Error } as AlbumsUiState.Error
        assertEquals(
            "Couldn't load your albums. The Immich server had an error (HTTP 500). Try again later.",
            error.message
        )

        fail = false
        viewModel.retry()
        assertEquals(AlbumsUiState.Ready(emptyList()), viewModel.state.await { it is AlbumsUiState.Ready })
    }
}

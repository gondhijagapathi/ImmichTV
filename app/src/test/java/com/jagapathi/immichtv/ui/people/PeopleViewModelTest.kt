package com.jagapathi.immichtv.ui.people

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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        FakeImmich(handler).let { PeopleViewModel(it.apiService, it.repository) }

    private fun person(id: String, name: String, isFavorite: Boolean = false, isHidden: Boolean = false) = """
        {"id": "$id", "name": "$name", "birthDate": null, "isFavorite": $isFavorite, "isHidden": $isHidden,
         "thumbnailPath": "/t/$id.jpeg", "updatedAt": "2024-05-01T10:00:00.000Z"}
    """

    @Test
    fun `lists named people who aren't hidden, favorites first`() = runBlocking {
        val viewModel = viewModel {
            respondJson(
                """{"hasNextPage": false, "people": [
                    ${person("most-photos", "Asha")}, ${person("unnamed", "")}, ${person("hidden", "Bo", isHidden = true)},
                    ${person("favorite", "Chen", isFavorite = true)}]}"""
            )
        }

        val people = (viewModel.state.await { it is PeopleUiState.Ready } as PeopleUiState.Ready).people

        assertEquals(listOf("favorite", "most-photos"), people.map { it.id })
        val asha = people[1]
        assertEquals("Asha", asha.name)
        assertEquals(
            "http://immich.local/api/people/most-photos/thumbnail?updatedAt=2024-05-01T10:00:00Z",
            asha.thumbnailUrl
        )
        assertEquals(TimelineQuery(personId = "most-photos", visibility = "timeline", withPartners = true), asha.timeline)
    }

    @Test
    fun `a failed load shows an error until a retry succeeds`() = runBlocking {
        var fail = true
        val viewModel = viewModel {
            if (fail) respondJson("{}", HttpStatusCode.InternalServerError) else respondJson("""{"people": []}""")
        }

        val error = viewModel.state.await { it is PeopleUiState.Error } as PeopleUiState.Error
        assertEquals("Couldn't load people. The Immich server had an error (HTTP 500). Try again later.", error.message)

        fail = false
        viewModel.retry()
        assertEquals(PeopleUiState.Ready(emptyList()), viewModel.state.await { it is PeopleUiState.Ready })
    }
}

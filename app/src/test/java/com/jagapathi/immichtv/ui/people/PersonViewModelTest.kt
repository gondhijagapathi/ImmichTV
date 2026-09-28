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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private lateinit var immich: FakeImmich

    private fun viewModel(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): PersonViewModel {
        immich = FakeImmich(handler)
        return PersonViewModel("person-1", immich.apiService, immich.repository)
    }

    private suspend fun PersonViewModel.awaitError() = state.await { it is PersonUiState.Error } as PersonUiState.Error

    private val asha = """{"id": "person-1", "name": "Asha", "birthDate": "1990-04-12", "isHidden": false,
        "thumbnailPath": "/t/1.jpeg"}"""

    @Test
    fun `loads the person and how many photos they're in`() = runBlocking {
        val viewModel = viewModel { request ->
            if (request.url.encodedPath.endsWith("/statistics")) respondJson("""{"assets": 1234}""") else respondJson(asha)
        }

        val ready = viewModel.state.await { it is PersonUiState.Ready } as PersonUiState.Ready

        assertEquals(
            setOf("/api/people/person-1", "/api/people/person-1/statistics"),
            immich.requests.map { it.url.encodedPath }.toSet()
        )
        assertEquals(1234, ready.assetCount)
        assertEquals("Asha", ready.person.name)
        assertEquals(LocalDate.of(1990, 4, 12), ready.person.birthDate)
        assertEquals(TimelineQuery(personId = "person-1", visibility = "timeline", withPartners = true), ready.person.timeline)
    }

    @Test
    fun `a person who's gone can't be retried`() = runBlocking {
        // What Immich answers for people who were deleted or merged into someone else.
        val viewModel = viewModel {
            respondJson("""{"message": "Not found or no person.read access"}""", HttpStatusCode.BadRequest)
        }

        val error = viewModel.awaitError()

        assertTrue(error.isGone)
        assertEquals(
            "This person isn't available anymore. They may have been removed or merged with someone else.",
            error.message
        )
    }

    @Test
    fun `other failures can be retried`() = runBlocking<Unit> {
        var fail = true
        val viewModel = viewModel { request ->
            when {
                // Either request failing fails the page.
                fail && request.url.encodedPath.endsWith("/statistics") -> respondJson("{}", HttpStatusCode.ServiceUnavailable)
                request.url.encodedPath.endsWith("/statistics") -> respondJson("""{"assets": 0}""")
                else -> respondJson(asha)
            }
        }

        val error = viewModel.awaitError()
        assertFalse(error.isGone)
        assertEquals(
            "Couldn't load this person. The Immich server had an error (HTTP 503). Try again later.",
            error.message
        )

        fail = false
        viewModel.retry()
        viewModel.state.await { it is PersonUiState.Ready }
    }
}

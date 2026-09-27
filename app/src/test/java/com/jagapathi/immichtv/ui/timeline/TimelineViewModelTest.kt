package com.jagapathi.immichtv.ui.timeline

import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.testing.FakeImmich
import com.jagapathi.immichtv.testing.await
import com.jagapathi.immichtv.testing.respondJson
import com.jagapathi.immichtv.ui.video.VideoPlayerFactory
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private lateinit var immich: FakeImmich

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): TimelineViewModel {
        immich = FakeImmich(handler)
        return TimelineViewModel(
            TimelineQuery.Library,
            immich.apiService,
            immich.repository,
            VideoPlayerFactory { throw UnsupportedOperationException("Not used in these tests") }
        )
    }

    private suspend fun TimelineViewModel.awaitLayout(predicate: (TimelineLayout) -> Boolean = { true }) =
        (state.await { it is TimelineUiState.Ready && predicate(it.layout) } as TimelineUiState.Ready).layout

    private val buckets = """[{"timeBucket": "2024-05-01", "count": 2}, {"timeBucket": "2024-04-01", "count": 1}]"""

    private fun bucket(vararg ids: String) = """
        {"id": [${ids.joinToString { "\"$it\"" }}],
         "fileCreatedAt": [${ids.joinToString { "\"2024-05-01T10:00:00\"" }}],
         "localOffsetHours": [${ids.joinToString { "0" }}],
         "isImage": [${ids.joinToString { "true" }}],
         "isFavorite": [${ids.joinToString { "false" }}],
         "duration": [${ids.joinToString { "null" }}],
         "thumbhash": [${ids.joinToString { "null" }}]}
    """

    @Test
    fun `lists months first and loads their assets on demand`() = runBlocking {
        val viewModel = viewModel { request ->
            when (request.url.encodedPath) {
                "/api/timeline/buckets" -> respondJson(buckets)
                else -> respondJson(bucket("a1", "a2"))
            }
        }

        val listed = viewModel.awaitLayout()
        assertEquals(listOf("2024-05-01", "2024-04-01"), listed.months.map { it.bucket })
        assertTrue(listed.months.all { it.assets == null })

        viewModel.loadMonths(0..0)
        val loaded = viewModel.awaitLayout { it.months[0].assets != null }
        assertEquals(listOf("a1", "a2"), loaded.months[0].assets?.map { it.id })
        assertNull(loaded.months[1].assets)

        val listRequest = immich.requests.first { it.url.encodedPath == "/api/timeline/buckets" }
        assertEquals("timeline", listRequest.url.parameters["visibility"])
        assertEquals("true", listRequest.url.parameters["withPartners"])
        assertEquals("true", listRequest.url.parameters["withStacked"])
        assertEquals("key", listRequest.headers["x-api-key"])
        val bucketRequest = immich.requests.first { it.url.encodedPath == "/api/timeline/bucket" }
        assertEquals("2024-05-01T00:00:00.000Z", bucketRequest.url.parameters["timeBucket"])

        assertEquals(
            "http://immich.local/api/assets/a1/thumbnail?size=thumbnail",
            viewModel.thumbnailUrl("a1")
        )
        assertEquals("http://immich.local/api/assets/a1/thumbnail?size=preview", viewModel.previewUrl("a1"))
        assertEquals("http://immich.local/api/assets/a1/video/playback", viewModel.videoUrl("a1"))
    }

    @Test
    fun `a month that fails to load reports why and loads when asked again`() = runBlocking<Unit> {
        var failNext = true
        val viewModel = viewModel { request ->
            when {
                request.url.encodedPath == "/api/timeline/buckets" -> respondJson(buckets)
                failNext -> {
                    failNext = false
                    respondJson("""{"message": "boom"}""", HttpStatusCode.InternalServerError)
                }
                else -> respondJson(bucket("a1", "a2"))
            }
        }
        viewModel.awaitLayout()

        viewModel.loadMonths(0..0)
        val error = viewModel.monthError.await { it != null }
        assertEquals(
            "Couldn't load some photos. The Immich server had an error (HTTP 500). Try again later.",
            error
        )
        assertNull((viewModel.state.value as TimelineUiState.Ready).layout.months[0].assets)

        viewModel.loadMonths(0..0)
        assertNotNull(viewModel.awaitLayout { it.months[0].assets != null }.months[0].assets)
        viewModel.monthError.await { it == null }
    }

    @Test
    fun `a failed timeline shows an error until a retry succeeds`() = runBlocking {
        var fail = true
        val viewModel = viewModel {
            if (fail) respondJson("{}", HttpStatusCode.Unauthorized) else respondJson(buckets)
        }

        val error = viewModel.state.await { it is TimelineUiState.Error } as TimelineUiState.Error
        assertEquals("Couldn't load your photos. Invalid API key.", error.message)

        fail = false
        viewModel.retry()
        assertEquals(2, viewModel.awaitLayout().months.size)
    }
}

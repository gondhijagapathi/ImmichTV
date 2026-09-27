package com.jagapathi.immichtv.ui.timeline

import androidx.datastore.core.DataStore
import com.jagapathi.immichtv.data.AppSettings
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.model.ImmichCredentials
import com.jagapathi.immichtv.model.TimelineQuery
import com.jagapathi.immichtv.model.UserProfile
import com.jagapathi.immichtv.network.ImmichApiService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {

    private class InMemoryDataStore(initial: AppSettings) : DataStore<AppSettings> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<AppSettings> = state
        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings =
            transform(state.value).also { state.value = it }
    }

    private val profile = UserProfile(
        id = "user-1",
        name = "User",
        profilePictureUrl = null,
        credentials = ImmichCredentials("http://immich.local", "key")
    )
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

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
        val client = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val repository = PreferenceRepository(
            InMemoryDataStore(AppSettings(profiles = listOf(profile), activeProfileId = profile.id)),
            CoroutineScope(Dispatchers.Unconfined)
        )
        return TimelineViewModel(TimelineQuery.Library, ImmichApiService(client, repository), repository)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = withTimeout(5_000) { first(predicate) }

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
                "/api/timeline/buckets" -> json(buckets)
                else -> json(bucket("a1", "a2"))
            }
        }

        val listed = viewModel.awaitLayout()
        assertEquals(listOf("2024-05-01", "2024-04-01"), listed.months.map { it.bucket })
        assertTrue(listed.months.all { it.assets == null })

        viewModel.loadMonths(0..0)
        val loaded = viewModel.awaitLayout { it.months[0].assets != null }
        assertEquals(listOf("a1", "a2"), loaded.months[0].assets?.map { it.id })
        assertNull(loaded.months[1].assets)

        val listRequest = requests.first { it.url.encodedPath == "/api/timeline/buckets" }
        assertEquals("timeline", listRequest.url.parameters["visibility"])
        assertEquals("true", listRequest.url.parameters["withPartners"])
        assertEquals("true", listRequest.url.parameters["withStacked"])
        assertEquals("key", listRequest.headers["x-api-key"])
        val bucketRequest = requests.first { it.url.encodedPath == "/api/timeline/bucket" }
        assertEquals("2024-05-01T00:00:00.000Z", bucketRequest.url.parameters["timeBucket"])

        assertEquals(
            "http://immich.local/api/assets/a1/thumbnail?size=thumbnail",
            viewModel.thumbnailUrl("a1")
        )
        assertEquals("http://immich.local/api/assets/a1/thumbnail?size=preview", viewModel.previewUrl("a1"))
    }

    @Test
    fun `a month that fails to load reports why and loads when asked again`() = runBlocking<Unit> {
        var failNext = true
        val viewModel = viewModel { request ->
            when {
                request.url.encodedPath == "/api/timeline/buckets" -> json(buckets)
                failNext -> {
                    failNext = false
                    json("""{"message": "boom"}""", HttpStatusCode.InternalServerError)
                }
                else -> json(bucket("a1", "a2"))
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
            if (fail) json("{}", HttpStatusCode.Unauthorized) else json(buckets)
        }

        val error = viewModel.state.await { it is TimelineUiState.Error } as TimelineUiState.Error
        assertEquals("Couldn't load your photos. Invalid API key.", error.message)

        fail = false
        viewModel.retry()
        assertEquals(2, viewModel.awaitLayout().months.size)
    }
}

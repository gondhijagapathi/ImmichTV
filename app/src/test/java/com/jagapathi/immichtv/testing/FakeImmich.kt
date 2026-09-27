package com.jagapathi.immichtv.testing

import androidx.datastore.core.DataStore
import com.jagapathi.immichtv.data.AppSettings
import com.jagapathi.immichtv.data.PreferenceRepository
import com.jagapathi.immichtv.model.ImmichCredentials
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.util.Collections

/**
 * A user signed in to a fake Immich server at `http://immich.local`, for view model tests.
 * [handler] answers each request, and [requests] records them.
 */
class FakeImmich(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) {
    val profile = UserProfile(
        id = "user-1",
        name = "User",
        profilePictureUrl = null,
        credentials = ImmichCredentials("http://immich.local", "key")
    )

    val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    val repository = PreferenceRepository(
        InMemoryDataStore(AppSettings(profiles = listOf(profile), activeProfileId = profile.id)),
        CoroutineScope(Dispatchers.Unconfined)
    )

    val apiService = ImmichApiService(
        HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        },
        repository
    )

    private class InMemoryDataStore(initial: AppSettings) : DataStore<AppSettings> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<AppSettings> = state
        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings =
            transform(state.value).also { state.value = it }
    }
}

fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

suspend fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = withTimeout(5_000) { first(predicate) }

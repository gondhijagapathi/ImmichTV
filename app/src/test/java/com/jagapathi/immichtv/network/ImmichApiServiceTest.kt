package com.jagapathi.immichtv.network

import com.jagapathi.immichtv.model.ImmichUserDto
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmichApiServiceTest {

    private val mockConfig = object : ImmichApiConfig {
        override val baseUrl: String = "http://localhost"
        override val apiKey: String = "dummy-key"
    }

    @Test
    fun `getUserMe returns correctly mapped user`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = Json.encodeToString(
                    ImmichUserDto(
                        id = "user-123",
                        name = "Immich User",
                        profileImagePath = "/path/to/image.jpg"
                    )
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val apiService = ImmichApiService(client, mockConfig)
        val user = apiService.getUserMe()

        assertEquals("user-123", user.id)
        assertEquals("Immich User", user.name)
    }

    @Test
    fun `getProfileImageUrl returns correct url`() {
        val apiService = ImmichApiService(config = mockConfig)
        val url = apiService.getProfileImageUrl("u1", "http://immich.local/")
        assertEquals("http://immich.local/api/users/u1/profile-image", url)
    }
}

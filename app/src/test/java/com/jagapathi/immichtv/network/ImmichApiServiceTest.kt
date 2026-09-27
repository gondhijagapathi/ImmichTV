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
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ImmichApiServiceTest {

    private val mockConfig = object : ImmichApiConfig {
        override val baseUrl: String = "http://localhost"
        override val apiKey: String = "dummy-key"
    }

    private fun jsonClient(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `getUserMe returns correctly mapped user`() = runBlocking {
        val client = jsonClient { request ->
            respond(
                content = Json.encodeToString(
                    ImmichUserDto(
                        id = "user-123",
                        name = "Immich User",
                        profileImagePath = "/path/to/image.jpg"
                    )
                ),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val apiService = ImmichApiService(client, mockConfig)
        val user = apiService.getUserMe()

        assertEquals("user-123", user.id)
        assertEquals("Immich User", user.name)
    }

    @Test
    fun `getProfileImageUrl returns correct url`() {
        val apiService = ImmichApiService(jsonClient { respondOk() }, mockConfig)
        val url = apiService.getProfileImageUrl("u1", "http://immich.local/")
        assertEquals("http://immich.local/api/users/u1/profile-image", url)
    }

    @Test
    fun `getAlbums parses Immich album responses`() = runBlocking {
        val client = jsonClient {
            respond(
                content = """
                    [{
                      "albumName": "Holidays",
                      "albumThumbnailAssetId": "asset-1",
                      "albumUsers": [],
                      "assetCount": 42,
                      "assets": [],
                      "createdAt": "2024-05-01T10:00:00.000Z",
                      "description": "Beach trip",
                      "hasSharedLink": false,
                      "id": "album-1",
                      "isActivityEnabled": true,
                      "order": "desc",
                      "owner": {"id": "user-1", "email": "a@b.c", "name": "A"},
                      "ownerId": "user-1",
                      "shared": true,
                      "updatedAt": "2024-05-02T10:00:00.000Z"
                    },
                    {
                      "albumName": "Empty",
                      "albumThumbnailAssetId": null,
                      "assetCount": 0,
                      "description": "",
                      "id": "album-2",
                      "ownerId": "user-1",
                      "shared": false
                    }]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val albums = ImmichApiService(client, mockConfig).getAlbums()

        assertEquals(2, albums.size)
        assertEquals("Holidays", albums[0].albumName)
        assertEquals("asset-1", albums[0].albumThumbnailAssetId)
        assertEquals(42, albums[0].assetCount)
        assertEquals(true, albums[0].shared)
        assertNull(albums[1].albumThumbnailAssetId)
    }

    @Test
    fun `getAllPeople follows pagination and parses dates`() = runBlocking {
        val requestedPages = mutableListOf<String?>()
        val client = jsonClient { request ->
            val page = request.url.parameters["page"]
            requestedPages += page
            val body = when (page) {
                "1" -> """{"hasNextPage": true, "hidden": 0, "total": 2, "people": [
                    {"id": "p1", "name": "Alice", "birthDate": "1990-04-12", "isFavorite": true, "isHidden": false,
                     "thumbnailPath": "/t/1.jpeg", "updatedAt": "2024-05-01T10:00:00.123Z"}]}"""
                else -> """{"hasNextPage": false, "hidden": 0, "total": 2, "people": [
                    {"id": "p2", "name": "Bob", "birthDate": null, "isHidden": false,
                     "thumbnailPath": "/t/2.jpeg", "updatedAt": "2024-05-01T12:00:00+02:00"}]}"""
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }

        val people = ImmichApiService(client, mockConfig).getAllPeople()

        assertEquals(listOf("1", "2"), requestedPages)
        assertEquals(listOf("p1", "p2"), people.map { it.id })
        assertEquals(LocalDate.of(1990, 4, 12), people[0].birthDate)
        assertEquals(Instant.parse("2024-05-01T10:00:00.123Z"), people[0].updatedAt)
        assertNull(people[1].birthDate)
        assertEquals(Instant.parse("2024-05-01T10:00:00Z"), people[1].updatedAt)
    }

    @Test
    fun `getAllPeople handles servers without pagination`() = runBlocking {
        var requests = 0
        val client = jsonClient {
            requests++
            respond("""{"hidden": 0, "total": 1, "people": [{"id": "p1", "name": "Alice"}]}""", HttpStatusCode.OK, jsonHeaders)
        }

        val people = ImmichApiService(client, mockConfig).getAllPeople()

        assertEquals(1, requests)
        assertEquals(listOf("p1"), people.map { it.id })
    }

    @Test
    fun `failed requests map to readable messages`() = runBlocking {
        suspend fun messageFor(status: HttpStatusCode, contentType: String = "application/json", body: String = "{}"): String {
            val client = jsonClient {
                respond(body, status, headersOf(HttpHeaders.ContentType, contentType))
            }
            try {
                ImmichApiService(client, mockConfig).getUserMe()
                fail("Expected the request to fail")
            } catch (e: Exception) {
                return e.toUserMessage()
            }
            error("unreachable")
        }

        assertEquals("Invalid API key.", messageFor(HttpStatusCode.Unauthorized))
        assertEquals("No Immich server found at this address.", messageFor(HttpStatusCode.NotFound))
        assertEquals(
            "The Immich server had an error (HTTP 502). Try again later.",
            messageFor(HttpStatusCode.BadGateway)
        )
        assertEquals(
            "Unexpected response. Is this an Immich server?",
            messageFor(HttpStatusCode.OK, "text/html", "<html>router login</html>")
        )
    }
}

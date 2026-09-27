package com.jagapathi.immichtv.network

import com.jagapathi.immichtv.model.ImmichUserDto
import com.jagapathi.immichtv.model.TimeBucketDto
import com.jagapathi.immichtv.model.TimelineQuery
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
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
import java.util.Collections

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
    fun `getAlbums parses album responses from Immich v2 and v3`() = runBlocking {
        val client = jsonClient {
            respond(
                content = """
                    [{
                      "albumName": "Holidays",
                      "albumThumbnailAssetId": "asset-1",
                      "albumUsers": [{"user": {"id": "user-2", "email": "b@b.c", "name": "B"}, "role": "editor"}],
                      "assetCount": 42,
                      "assets": [],
                      "createdAt": "2024-05-01T10:00:00.000Z",
                      "description": "Beach trip",
                      "startDate": "2024-04-28T08:00:00.000Z",
                      "endDate": "2024-05-01T18:30:00.000Z",
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
                      "albumName": "Shared with me",
                      "albumThumbnailAssetId": null,
                      "albumUsers": [
                        {"user": {"id": "user-2", "email": "b@b.c", "name": "B"}, "role": "owner"},
                        {"user": {"id": "user-1", "email": "a@b.c", "name": "A"}, "role": "viewer"}
                      ],
                      "assetCount": 0,
                      "contributorCounts": [],
                      "createdAt": "2024-05-01T10:00:00.000Z",
                      "description": null,
                      "hasSharedLink": false,
                      "id": "album-2",
                      "isActivityEnabled": true,
                      "order": "asc",
                      "shared": true,
                      "updatedAt": "2024-05-02T10:00:00.000Z"
                    }]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val albums = ImmichApiService(client, mockConfig).getAlbums()

        assertEquals(2, albums.size)
        val (v2, v3) = albums
        assertEquals("Holidays", v2.albumName)
        assertEquals("asset-1", v2.albumThumbnailAssetId)
        assertEquals(42, v2.assetCount)
        assertEquals(Instant.parse("2024-04-28T08:00:00Z"), v2.startDate)
        assertEquals(Instant.parse("2024-05-01T18:30:00Z"), v2.endDate)
        // v2 sends the owner separately from the people the album is shared with.
        assertEquals("user-1", v2.albumOwner?.id)
        assertNull(v3.albumThumbnailAssetId)
        assertNull(v3.description)
        assertNull(v3.endDate)
        assertEquals("asc", v3.order)
        // v3 lists the owner first among the album's users.
        assertEquals("user-2", v3.albumOwner?.id)
    }

    @Test
    fun `getAllAlbums merges own and shared albums`() = runBlocking {
        val requests = Collections.synchronizedList(mutableListOf<HttpRequestData>())
        fun album(id: String) = """{"id": "$id", "albumName": "$id"}"""
        val client = jsonClient { request ->
            requests += request
            // Like servers before v3; v3 ignores `shared` and lists every album both times.
            val body = when (request.url.parameters["shared"]) {
                "true" -> "[${album("shared-with-me")}, ${album("shared-by-me")}]"
                else -> "[${album("mine")}, ${album("shared-by-me")}]"
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }

        val albums = ImmichApiService(client, mockConfig).getAllAlbums()

        assertEquals(listOf("mine", "shared-by-me", "shared-with-me"), albums.map { it.id })
        assertEquals(setOf(null, "true"), requests.map { it.url.parameters["shared"] }.toSet())
    }

    @Test
    fun `getAlbum asks for the album without its assets`() = runBlocking {
        var request: HttpRequestData? = null
        val client = jsonClient {
            request = it
            respond("""{"id": "album-1", "albumName": "Tokyo"}""", HttpStatusCode.OK, jsonHeaders)
        }

        val album = ImmichApiService(client, mockConfig).getAlbum("album-1")

        assertEquals("Tokyo", album.albumName)
        assertEquals("http://localhost/api/albums/album-1?withoutAssets=true", request?.url.toString())
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
    fun `timeline requests send only the filters that are set`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val client = jsonClient { request ->
            requests += request
            val body = if (request.url.encodedPath.endsWith("buckets")) {
                """[{"timeBucket": "2024-05-01", "count": 3}]"""
            } else {
                """{"id": [], "fileCreatedAt": []}"""
            }
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        val apiService = ImmichApiService(client, mockConfig)
        val albumQuery = TimelineQuery(albumId = "album-1", order = "asc")

        val buckets = apiService.getTimeBuckets(albumQuery)
        // Older servers list buckets as full timestamps, which are passed back unchanged.
        apiService.getTimeBucket("2024-05-01T00:00:00.000Z", albumQuery)

        assertEquals(listOf(TimeBucketDto("2024-05-01", 3)), buckets)
        assertEquals(
            mapOf("albumId" to listOf("album-1"), "order" to listOf("asc")),
            requests[0].url.parameters.entries().associate { it.key to it.value }
        )
        assertEquals("2024-05-01T00:00:00.000Z", requests[1].url.parameters["timeBucket"])
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

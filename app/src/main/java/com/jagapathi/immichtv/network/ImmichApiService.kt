package com.jagapathi.immichtv.network

import com.jagapathi.immichtv.model.ImmichAlbumDto
import com.jagapathi.immichtv.model.ImmichPeopleDto
import com.jagapathi.immichtv.model.ImmichPersonResponseDto
import com.jagapathi.immichtv.model.ImmichUserDto
import com.jagapathi.immichtv.model.TimeBucketAssetsDto
import com.jagapathi.immichtv.model.TimeBucketDto
import com.jagapathi.immichtv.model.TimelineQuery
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class ImmichApiService(
    private val client: HttpClient = createDefaultClient(),
    private val config: ImmichApiConfig
) {
    val baseUrl: String? get() = config.baseUrl
    val apiKey: String? get() = config.apiKey

    private fun getFullUrl(baseUrl: String, endpoint: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        // If the URL already contains /api at the end, don't add it again
        val urlWithApi = if (normalizedBase.endsWith("/api")) {
            normalizedBase
        } else {
            "$normalizedBase/api"
        }
        return "$urlWithApi/${endpoint.trimStart('/')}"
    }

    private fun requireBaseUrl(override: String?): String {
        return override ?: config.baseUrl ?: throw IllegalStateException("Server URL not configured")
    }

    private fun requireApiKey(override: String?): String {
        return override ?: config.apiKey ?: throw IllegalStateException("API Key not configured")
    }

    suspend fun getUserMe(serverUrl: String? = null, apiKey: String? = null): ImmichUserDto {
        val url = requireBaseUrl(serverUrl)
        val key = requireApiKey(apiKey)
        return withContext(Dispatchers.IO) {
            client.get(getFullUrl(url, "users/me")) {
                header("x-api-key", key)
            }.body()
        }
    }

    fun getProfileImageUrl(userId: String, serverUrl: String? = null): String {
        val url = requireBaseUrl(serverUrl)
        return getFullUrl(url, "users/$userId/profile-image")
    }

    suspend fun getAlbums(): List<ImmichAlbumDto> {
        val url = requireBaseUrl(null)
        val key = requireApiKey(null)
        return withContext(Dispatchers.IO) {
            client.get(getFullUrl(url, "albums")) {
                header("x-api-key", key)
            }.body()
        }
    }

    suspend fun getPeople(page: Int = 1, size: Int = PEOPLE_PAGE_SIZE): ImmichPeopleDto {
        val url = requireBaseUrl(null)
        val key = requireApiKey(null)
        return withContext(Dispatchers.IO) {
            client.get(getFullUrl(url, "people")) {
                header("x-api-key", key)
                parameter("page", page)
                parameter("size", size)
            }.body()
        }
    }

    /** Fetches every page of visible people. */
    suspend fun getAllPeople(): List<ImmichPersonResponseDto> {
        val people = mutableListOf<ImmichPersonResponseDto>()
        var page = 1
        while (true) {
            val response = getPeople(page)
            people += response.people
            // Servers that predate pagination omit hasNextPage and return everyone at once.
            if (!response.hasNextPage || response.people.isEmpty()) break
            page++
        }
        return people.distinctBy { it.id }
    }

    fun getPersonThumbnailUrl(id: String): String {
        val url = requireBaseUrl(null)
        return getFullUrl(url, "people/$id/thumbnail")
    }

    /** Lists the months that have assets matching [query], newest first. */
    suspend fun getTimeBuckets(query: TimelineQuery): List<TimeBucketDto> {
        val url = requireBaseUrl(null)
        val key = requireApiKey(null)
        return withContext(Dispatchers.IO) {
            client.get(getFullUrl(url, "timeline/buckets")) {
                header("x-api-key", key)
                timelineFilters(query)
            }.body()
        }
    }

    /** Fetches the assets of one month, newest first. [timeBucket] comes from [getTimeBuckets]. */
    suspend fun getTimeBucket(timeBucket: String, query: TimelineQuery): TimeBucketAssetsDto {
        val url = requireBaseUrl(null)
        val key = requireApiKey(null)
        return withContext(Dispatchers.IO) {
            client.get(getFullUrl(url, "timeline/bucket")) {
                header("x-api-key", key)
                timelineFilters(query)
                // Buckets are listed as dates, but this endpoint documents a full timestamp.
                parameter("timeBucket", if ('T' in timeBucket) timeBucket else "${timeBucket}T00:00:00.000Z")
            }.body()
        }
    }

    private fun HttpRequestBuilder.timelineFilters(query: TimelineQuery) {
        parameter("albumId", query.albumId)
        parameter("personId", query.personId)
        parameter("isFavorite", query.isFavorite)
        parameter("visibility", query.visibility)
        if (query.withPartners) parameter("withPartners", true)
        if (query.withStacked) parameter("withStacked", true)
    }

    fun getAssetThumbnailUrl(assetId: String, size: ThumbnailSize, serverUrl: String? = null): String {
        val url = requireBaseUrl(serverUrl)
        return getFullUrl(url, "assets/$assetId/thumbnail?size=${size.value}")
    }

    enum class ThumbnailSize(val value: String) {
        /** Small, for grids (250px by default). */
        Thumbnail("thumbnail"),

        /** Large enough to fill a screen (1440px by default). */
        Preview("preview")
    }

    companion object {
        const val PEOPLE_PAGE_SIZE = 500

        fun createDefaultClient(okHttpClient: OkHttpClient = OkHttpClient()) = HttpClient(OkHttp) {
            expectSuccess = true
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
    }
}

package com.jagapathi.immichtv.network

import androidx.compose.runtime.staticCompositionLocalOf
import com.jagapathi.immichtv.model.ImmichAlbumDto
import com.jagapathi.immichtv.model.ImmichPeopleDto
import com.jagapathi.immichtv.model.ImmichUserDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

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

    suspend fun getPeople(): ImmichPeopleDto {
        val url = requireBaseUrl(null)
        val key = requireApiKey(null)
        return withContext(Dispatchers.IO) {
            client.get(getFullUrl(url, "people")) {
                header("x-api-key", key)
            }.body()
        }
    }

    fun getPersonThumbnailUrl(id: String): String {
        val url = requireBaseUrl(null)
        return getFullUrl(url, "people/$id/thumbnail")
    }

    companion object {
        fun createDefaultClient() = HttpClient(CIO) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
    }
}

val LocalImmichApiService = staticCompositionLocalOf<ImmichApiService> {
    error("No ImmichApiService provided")
}

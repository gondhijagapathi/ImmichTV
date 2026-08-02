package com.jagapathi.immichtv.network

import com.jagapathi.immichtv.model.ImmichUserDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ImmichApiService(private val client: HttpClient = createDefaultClient()) {
    
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

    suspend fun getUserMe(serverUrl: String, apiKey: String): ImmichUserDto {
        return client.get(getFullUrl(serverUrl, "users/me")) {
            header("x-api-key", apiKey)
        }.body()
    }

    fun getProfileImageUrl(serverUrl: String, userId: String): String {
        return getFullUrl(serverUrl, "users/$userId/profile-image")
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

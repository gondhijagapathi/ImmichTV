package com.jagapathi.immichtv.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds the active profile's API key to requests for that profile's server, so image loading
 * doesn't need to thread credentials through every composable. Requests to any other origin are
 * left untouched so the key never leaks to a third party.
 */
class ApiKeyInterceptor(private val config: ImmichApiConfig) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = config.apiKey
        val server = config.baseUrl?.trim()?.toHttpUrlOrNull()

        if (apiKey == null || server == null || request.header(API_KEY_HEADER) != null ||
            !request.url.hasSameOrigin(server)
        ) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header(API_KEY_HEADER, apiKey).build())
    }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl) =
        scheme == other.scheme && host == other.host && port == other.port

    companion object {
        const val API_KEY_HEADER = "x-api-key"
    }
}

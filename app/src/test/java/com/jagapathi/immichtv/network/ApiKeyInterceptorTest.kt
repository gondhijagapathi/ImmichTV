package com.jagapathi.immichtv.network

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyInterceptorTest {

    private val config = object : ImmichApiConfig {
        override var baseUrl: String? = "https://photos.example.com"
        override var apiKey: String? = "secret"
    }

    /** Sends a request through the interceptor and returns the API key header that would go out. */
    private fun sentApiKey(url: String): String? {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(ApiKeyInterceptor(config))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return sent!!.header(ApiKeyInterceptor.API_KEY_HEADER)
    }

    @Test
    fun `adds the key for the active server`() {
        assertEquals("secret", sentApiKey("https://photos.example.com/api/people/p1/thumbnail"))
    }

    @Test
    fun `never sends the key to other origins`() {
        assertNull(sentApiKey("https://evil.example.com/api/people/p1/thumbnail"))
        assertNull(sentApiKey("http://photos.example.com/api/people/p1/thumbnail"))
        assertNull(sentApiKey("https://photos.example.com:8443/api/people/p1/thumbnail"))
    }

    @Test
    fun `does nothing when logged out`() {
        config.baseUrl = null
        config.apiKey = null
        assertNull(sentApiKey("https://photos.example.com/api/people/p1/thumbnail"))
    }
}

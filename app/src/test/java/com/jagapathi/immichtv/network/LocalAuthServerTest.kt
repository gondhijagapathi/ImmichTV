package com.jagapathi.immichtv.network

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class LocalAuthServerTest {

    private val received = mutableListOf<Pair<String, String>>()
    private var loginError: String? = null
    private val server = LocalAuthServer { url, key ->
        received += url to key
        loginError
    }

    private fun test(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application { server.configure(this) }
        block()
    }

    private suspend fun HttpClient.submit(path: String, serverUrl: String = "https://photos.example.com", apiKey: String = "key") =
        submitForm(path, parameters {
            append("serverUrl", serverUrl)
            append("apiKey", apiKey)
        })

    @Test
    fun `serves the login form only with the current pairing code`() = test {
        val code = server.pairingCode.value

        assertEquals(HttpStatusCode.OK, client.get("/$code").status)
        assertEquals(HttpStatusCode.OK, client.get("/${code.lowercase()}").status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/ABCDEFGH".takeIf { it != code } ?: "/HGFEDCBA").status)
        assertTrue(client.get("/").bodyAsText().contains("Scan the QR code"))
    }

    @Test
    fun `rejects credentials sent without the pairing code`() = test {
        val wrongCode = if (server.pairingCode.value == "ABCDEFGH") "HGFEDCBA" else "ABCDEFGH"

        assertEquals(HttpStatusCode.Forbidden, client.submit("/$wrongCode").status)
        assertEquals(HttpStatusCode.NotFound, client.submit("/login").status)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `hands credentials over and retires the code after a successful login`() = test {
        val code = server.pairingCode.value

        val response = client.submit("/$code")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Success"))
        assertEquals(listOf("https://photos.example.com" to "key"), received)
        assertNotEquals(code, server.pairingCode.value)
        assertEquals(HttpStatusCode.Forbidden, client.submit("/$code").status)
    }

    @Test
    fun `shows the login error on the phone and keeps the code for a retry`() = test {
        val code = server.pairingCode.value
        loginError = "Invalid API key."

        val response = client.submit("/$code", serverUrl = "https://photos.example.com/\"><script>")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(body.contains("Invalid API key."))
        assertTrue("user input must be escaped", body.contains("&quot;&gt;&lt;script&gt;"))
        assertEquals(code, server.pairingCode.value)
    }

    @Test
    fun `browser requests like favicon do not count as guesses`() = test {
        val code = server.pairingCode.value

        repeat(LocalAuthServer.MAX_FAILED_ATTEMPTS * 2) {
            assertEquals(HttpStatusCode.NotFound, client.get("/favicon.ico").status)
        }

        assertEquals(code, server.pairingCode.value)
    }

    @Test
    fun `rotates the code after too many wrong guesses`() = test {
        val code = server.pairingCode.value
        val wrongCode = if (code == "ABCDEFGH") "HGFEDCBA" else "ABCDEFGH"

        repeat(LocalAuthServer.MAX_FAILED_ATTEMPTS) { client.get("/$wrongCode") }

        assertNotEquals(code, server.pairingCode.value)
        assertEquals(HttpStatusCode.Forbidden, client.get("/$code").status)
    }

    @Test
    fun `falls back to a free port when the preferred one is taken`() = runBlocking {
        ServerSocket(0).use { blocker ->
            val authServer = LocalAuthServer(preferredPort = blocker.localPort) { _, _ -> null }
            try {
                val port = authServer.start()
                assertNotEquals(blocker.localPort, port)
                HttpClient().use { client ->
                    val response = client.get("http://127.0.0.1:$port/")
                    assertEquals(HttpStatusCode.OK, response.status)
                }
            } finally {
                authServer.stop()
            }
        }
    }
}

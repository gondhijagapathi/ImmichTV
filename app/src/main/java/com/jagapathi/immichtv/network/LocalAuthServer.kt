package com.jagapathi.immichtv.network

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Small web server the phone uses to hand credentials to the TV. Pages are only served under a
 * one-time pairing code that is shown on the TV (inside the QR code), so other devices on the
 * network can't log the TV in. The code is replaced after a successful login and after
 * [MAX_FAILED_ATTEMPTS] wrong guesses.
 *
 * [onCredentialsReceived] returns null on success, or an error message to show on the phone.
 */
class LocalAuthServer(
    private val preferredPort: Int = DEFAULT_PORT,
    private val onCredentialsReceived: suspend (serverUrl: String, apiKey: String) -> String?
) {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val random = SecureRandom()
    private val failedAttempts = AtomicInteger(0)

    // CIO reports a failed bind (e.g. port already in use) to start() and also as an uncaught
    // coroutine exception, which would crash the app. start() already handles it, so only log here.
    private val serverScope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, e ->
        println("LocalAuthServer: ${e.message}")
    })

    private val _pairingCode = MutableStateFlow(newPairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    /**
     * Starts listening and returns the port. [preferredPort] keeps the address the same between
     * launches; if another app already holds it, any free port is used instead.
     */
    suspend fun start(): Int = withContext(Dispatchers.IO) {
        val started = try {
            startServer(preferredPort)
        } catch (e: Exception) {
            // A busy port surfaces as a cancellation, so only give up if we were really cancelled.
            ensureActive()
            startServer(0)
        }
        server = started
        started.engine.resolvedConnectors().first().port
    }

    fun stop() {
        val running = server ?: return
        server = null
        // Stopping waits for in-flight responses, so keep it off the caller's (main) thread.
        thread(name = "LocalAuthServer-stop") { running.stop(500, 1000) }
    }

    private fun startServer(port: Int) =
        serverScope.embeddedServer(CIO, port = port) { configure(this) }.also { candidate ->
            try {
                candidate.start(wait = false)
            } catch (e: Exception) {
                candidate.stop(0, 0)
                throw e
            }
        }

    internal fun configure(application: Application) {
        application.routing {
            get("/") {
                call.respondPage(HttpStatusCode.OK, messagePage("Scan the QR code on your TV to log in."))
            }
            get("/{code}") {
                if (checkCode(call)) {
                    call.respondPage(HttpStatusCode.OK, formPage())
                }
            }
            post("/{code}") {
                if (!checkCode(call)) return@post

                val params = call.receiveParameters()
                val serverUrl = params["serverUrl"]
                val apiKey = params["apiKey"]
                if (serverUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
                    call.respondPage(
                        HttpStatusCode.BadRequest,
                        formPage(serverUrl.orEmpty(), "Both the server URL and API key are required.")
                    )
                    return@post
                }

                val error = onCredentialsReceived(serverUrl, apiKey)
                if (error == null) {
                    // The link has done its job; make sure it can't be replayed.
                    rotateCode()
                    call.respondPage(HttpStatusCode.OK, messagePage("Success! You can close this tab and look at your TV."))
                } else {
                    call.respondPage(HttpStatusCode.BadRequest, formPage(serverUrl, error))
                }
            }
        }
    }

    /** Responds with an error page and returns false unless the request carries the current code. */
    private suspend fun checkCode(call: ApplicationCall): Boolean {
        val code = call.parameters["code"].orEmpty().uppercase()
        // Only code-shaped paths count as guesses, so browser requests like /favicon.ico
        // can't burn through the attempts and invalidate a real user's link.
        if (code.length != CODE_LENGTH || code.any { it !in CODE_ALPHABET }) {
            call.respondPage(HttpStatusCode.NotFound, messagePage("Page not found."))
            return false
        }
        if (MessageDigest.isEqual(code.toByteArray(), _pairingCode.value.toByteArray())) return true

        if (failedAttempts.incrementAndGet() >= MAX_FAILED_ATTEMPTS) rotateCode()
        call.respondPage(
            HttpStatusCode.Forbidden,
            messagePage("This login link is no longer valid. Scan the QR code on your TV again.")
        )
        return false
    }

    private fun rotateCode() {
        failedAttempts.set(0)
        _pairingCode.value = newPairingCode()
    }

    private fun newPairingCode() = String(CharArray(CODE_LENGTH) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] })

    private suspend fun ApplicationCall.respondPage(status: HttpStatusCode, html: String) {
        response.headers.append(HttpHeaders.CacheControl, "no-store")
        respondText(html, ContentType.Text.Html, status)
    }

    private fun formPage(serverUrl: String = "", error: String? = null) = page(
        """
        <form method="post">
            <h2>Immich TV Login</h2>
            ${error?.let { """<p class="error">${it.escapeHtml()}</p>""" } ?: ""}
            <input type="url" name="serverUrl" placeholder="Server URL (https://...)" value="${serverUrl.escapeHtml()}" autocapitalize="off" required>
            <input type="password" name="apiKey" placeholder="API Key" required>
            <button type="submit">Login to TV</button>
        </form>
        """
    )

    private fun messagePage(message: String) = page(
        """<div class="card"><h2>Immich TV</h2><p>${message.escapeHtml()}</p></div>"""
    )

    private fun page(body: String) = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Immich TV Login</title>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; background-color: #f0f2f5; }
                form, .card { background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); width: 90%; max-width: 400px; box-sizing: border-box; }
                h2 { margin-top: 0; color: #1c1e21; }
                .error { color: #c62828; }
                input { width: 100%; padding: 12px; margin: 8px 0; box-sizing: border-box; border: 1px solid #ddd; border-radius: 4px; }
                button { width: 100%; padding: 12px; background-color: #2342c0; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; margin-top: 16px;}
                button:hover { background-color: #1a33a0; }
            </style>
        </head>
        <body>
            $body
        </body>
        </html>
    """.trimIndent()

    private fun String.escapeHtml() = buildString {
        for (c in this@escapeHtml) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(c)
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val MAX_FAILED_ATTEMPTS = 10
        private const val CODE_LENGTH = 8
        // No 0/O or 1/I so the code is easy to type from the TV screen.
        private const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    }
}

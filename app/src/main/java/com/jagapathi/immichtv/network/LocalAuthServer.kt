package com.jagapathi.immichtv.network

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class LocalAuthServer(private val onCredentialsReceived: (String, String) -> Unit) {
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    fun start() {
        server = embeddedServer(Netty, port = 8080) {
            routing {
                get("/") {
                    call.respondText(getHtml(), io.ktor.http.ContentType.Text.Html)
                }
                get("/login") {
                    val serverUrl = call.parameters["serverUrl"]
                    val apiKey = call.parameters["apiKey"]
                    if (serverUrl != null && apiKey != null) {
                        onCredentialsReceived(serverUrl, apiKey)
                        call.respondText("Success! You can close this tab and look at your TV.")
                    } else {
                        call.respondText("Missing parameters", status = io.ktor.http.HttpStatusCode.BadRequest)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }

    private fun getHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Immich TV Login</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: sans-serif; display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; margin: 0; background-color: #f0f2f5; }
                    form { background: white; padding: 2rem; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); width: 90%; max-width: 400px; }
                    h2 { margin-top: 0; color: #1c1e21; }
                    input { width: 100%; padding: 12px; margin: 8px 0; box-sizing: border-box; border: 1px solid #ddd; border-radius: 4px; }
                    button { width: 100%; padding: 12px; background-color: #2342c0; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 16px; margin-top: 16px;}
                    button:hover { background-color: #1a33a0; }
                </style>
            </head>
            <body>
                <form action="/login" method="get">
                    <h2>Immich TV Login</h2>
                    <input type="text" name="serverUrl" placeholder="Server URL (https://...)" required>
                    <input type="password" name="apiKey" placeholder="API Key" required>
                    <button type="submit">Login to TV</button>
                </form>
            </body>
            </html>
        """.trimIndent()
    }
}

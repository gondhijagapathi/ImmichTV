package com.jagapathi.immichtv.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Trims user input down to a usable server address, or returns null if it isn't an
 * http(s) URL. The scheme is required rather than guessed: guessing http would send the
 * API key in plain text to servers that expect https.
 */
fun normalizeServerUrl(input: String): String? {
    val url = input.trim().trimEnd('/')
    return url.takeIf { it.toHttpUrlOrNull() != null }
}

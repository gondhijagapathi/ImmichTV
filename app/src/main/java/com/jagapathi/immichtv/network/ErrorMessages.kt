package com.jagapathi.immichtv.network

import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Turns a failed Immich request into a message that makes sense on the TV screen. */
fun Throwable.toUserMessage(): String = when (this) {
    is ClientRequestException -> when (response.status.value) {
        401 -> "Invalid API key."
        403 -> "This API key is missing permissions ImmichTV needs."
        404 -> "No Immich server found at this address."
        else -> "The server rejected the request (HTTP ${response.status.value})."
    }
    is ServerResponseException ->
        "The Immich server had an error (HTTP ${response.status.value}). Try again later."
    is UnknownHostException -> "Couldn't find the server. Check the address."
    is SocketTimeoutException, is HttpRequestTimeoutException -> "The server took too long to respond."
    is ConnectException -> "Couldn't connect to the server. Make sure it's running and reachable from the TV."
    is SSLException -> "Secure connection failed. Check the server's certificate."
    is NoTransformationFoundException, is ContentConvertException, is SerializationException ->
        "Unexpected response. Is this an Immich server?"
    else -> "Something went wrong: ${localizedMessage ?: javaClass.simpleName}"
}

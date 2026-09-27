package com.jagapathi.immichtv.model

import kotlinx.serialization.Serializable

@Serializable
data class ImmichCredentials(
    val serverUrl: String,
    val apiKey: String
)

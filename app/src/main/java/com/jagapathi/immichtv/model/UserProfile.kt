package com.jagapathi.immichtv.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val profilePictureUrl: String?,
    val credentials: ImmichCredentials
)

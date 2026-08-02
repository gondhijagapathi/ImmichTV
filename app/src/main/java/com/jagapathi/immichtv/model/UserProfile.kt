package com.jagapathi.immichtv.model

data class UserProfile(
    val id: String,
    val name: String,
    val profilePictureUrl: String?,
    val credentials: ImmichCredentials
)

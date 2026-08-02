package com.jagapathi.immichtv.model

import kotlinx.serialization.Serializable

@Serializable
data class ImmichUserDto(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val profileImagePath: String? = null
)

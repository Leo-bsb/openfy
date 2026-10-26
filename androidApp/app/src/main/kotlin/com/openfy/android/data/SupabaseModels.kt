package com.openfy.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String = "",
    val email: String? = null,
)

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: AuthUser? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val data: Map<String, String> = emptyMap(),
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class Profile(
    val id: String = "",
    val handle: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)

@Serializable
data class PlaylistDto(
    val id: String = "",
    val name: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
    val deleted: Boolean = false,
)

@Serializable
data class CreatePlaylistRequest(
    val name: String,
    val description: String = "",
)

@Serializable
data class DeviceUpsert(
    val id: String,
    val name: String,
    val platform: String = "android",
    @SerialName("last_seen_at") val lastSeenAt: String,
)

@Serializable
data class ApiError(
    val message: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

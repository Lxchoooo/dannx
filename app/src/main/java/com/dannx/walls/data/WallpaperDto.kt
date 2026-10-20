package com.dannx.walls.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WallpaperDto(
    val id: String,
    val title: String = "",
    val category: String = "Abstracto",
    @SerialName("storage_path") val storagePath: String = "",
    val author: String = "DannX",
    val width: Int = 0,
    val height: Int = 0,
    val featured: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class NewWallpaper(
    val title: String,
    val category: String,
    @SerialName("storage_path") val storagePath: String,
    val author: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class AuthUser(
    val id: String = "",
    val email: String? = null,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    val user: AuthUser? = null,
)

@Serializable
data class DownloadRequest(
    @SerialName("wallpaper_id") val wallpaperId: String,
)

fun WallpaperDto.toWallpaper(baseUrl: String): Wallpaper = Wallpaper(
    id = id,
    title = title.ifBlank { "Sin título" },
    category = category.ifBlank { "Abstracto" },
    imageUrl = "$baseUrl/storage/v1/object/public/wallpapers/$storagePath",
    author = author.ifBlank { "DannX" },
    width = width,
    height = height,
    featured = featured,
    createdAt = createdAt,
)

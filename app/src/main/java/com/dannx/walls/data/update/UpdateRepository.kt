package com.dannx.walls.data.update

import java.io.File
import java.io.IOException

data class ReleaseInfo(
    val version: String,
    val tagName: String,
    val notes: String,
    val apkUrl: String,
    val checksumUrl: String,
    val htmlUrl: String,
    val publishedAt: String,
)

sealed class UpdateError(message: String) : IOException(message) {
    data class Network(val detail: String) : UpdateError(detail)
    data object NoRelease : UpdateError("No release available")
    data object Format : UpdateError("Invalid release version")
    data class MissingAsset(val name: String) : UpdateError("Missing asset: $name")
    data object Checksum : UpdateError("Checksum mismatch")
    data class Download(val detail: String) : UpdateError(detail)
}

interface UpdateRepository {
    suspend fun latestRelease(): ReleaseInfo

    suspend fun downloadVerified(
        release: ReleaseInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File
}

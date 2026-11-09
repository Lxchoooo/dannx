package com.dannx.walls.data.update

import android.content.Context
import com.dannx.walls.data.WallsApi
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Consulta la API pública de GitHub y descarga el APK de la última release.
 * La lista se ordena por la versión del tag, no por la fecha de publicación,
 * para que la release rodante o una release vieja no oculten una versión nueva.
 */
class GitHubUpdateRepository(
    private val context: Context,
    private val client: OkHttpClient = WallsApi.defaultClient(),
) : UpdateRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun latestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        try {
            val body = requestText(
                url = "$REPO_URL/releases?per_page=30",
                accept = "application/vnd.github+json",
            )
            val releases = json.decodeFromString<List<GitHubReleaseDto>>(body)
            val candidates = releases
                .asSequence()
                .filterNot { it.draft || it.prerelease }
                .mapNotNull { release ->
                    AppVersion.parse(release.tagName)?.let { version -> version to release }
                }
                .sortedByDescending { it.first }
                .toList()
            if (candidates.isEmpty()) throw UpdateError.NoRelease

            val selected = candidates.firstOrNull { (_, release) ->
                release.assets.any { it.name == APK_NAME } &&
                    release.assets.any { it.name == CHECKSUM_NAME }
            } ?: candidates.first()
            val (version, release) = selected
            val apk = release.assets.firstOrNull { it.name == APK_NAME }
                ?: throw UpdateError.MissingAsset(APK_NAME)
            val checksum = release.assets.firstOrNull { it.name == CHECKSUM_NAME }
                ?: throw UpdateError.MissingAsset(CHECKSUM_NAME)
            ReleaseInfo(
                version = version.raw,
                tagName = release.tagName,
                notes = release.body.orEmpty(),
                apkUrl = apk.downloadUrl,
                checksumUrl = checksum.downloadUrl,
                htmlUrl = release.htmlUrl.orEmpty(),
                publishedAt = release.publishedAt.orEmpty(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: UpdateError) {
            throw error
        } catch (error: Exception) {
            throw UpdateError.Network(error.message.orEmpty())
        }
    }

    override suspend fun downloadVerified(
        release: ReleaseInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, UPDATES_FOLDER).apply { mkdirs() }
        val target = File(folder, "DannX-${release.version}.apk")
        val temporary = File(folder, "${target.name}.part")
        temporary.delete()
        folder.listFiles()
            ?.filterNot { it == target || it == temporary }
            ?.forEach { it.delete() }

        try {
            val expected = fetchChecksum(release.checksumUrl)
            val request = Request.Builder()
                .url(release.apkUrl)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateError.Download("HTTP ${response.code}")
                }
                val body = response.body
                    ?: throw UpdateError.Download("Respuesta vacía")
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
                if (total > 0 && downloaded != total) {
                    throw UpdateError.Download("Descarga incompleta")
                }
                val actual = digest.digest()
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw UpdateError.Checksum
                }
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target
            }
        } catch (error: CancellationException) {
            temporary.delete()
            throw error
        } catch (error: UpdateError) {
            temporary.delete()
            throw error
        } catch (error: Exception) {
            temporary.delete()
            throw UpdateError.Download(error.message.orEmpty())
        }
    }

    private fun fetchChecksum(url: String): String {
        val text = requestText(url = url, accept = "text/plain")
        val hash = text.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (!HASH_PATTERN.matches(hash)) {
            throw UpdateError.MissingAsset(CHECKSUM_NAME)
        }
        return hash
    }

    private fun requestText(url: String, accept: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 404) throw UpdateError.NoRelease
                throw UpdateError.Network("HTTP ${response.code}")
            }
            body
        }
    }

    @Serializable
    private data class GitHubReleaseDto(
        @SerialName("tag_name") val tagName: String = "",
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GitHubAssetDto> = emptyList(),
    )

    @Serializable
    private data class GitHubAssetDto(
        val name: String = "",
        @SerialName("browser_download_url") val downloadUrl: String = "",
    )

    private companion object {
        const val REPO_URL = "https://api.github.com/repos/Lxchoooo/dannx"
        const val APK_NAME = "DannX.apk"
        const val CHECKSUM_NAME = "DannX.apk.sha256"
        const val UPDATES_FOLDER = "updates"
        const val USER_AGENT = "DannX-Android"
        val HASH_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}

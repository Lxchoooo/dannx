package com.dannx.walls.data

import android.content.Context
import android.net.Uri
import com.dannx.walls.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

class WallsApi(
    private val context: Context,
    private val prefs: Prefs,
    private val client: OkHttpClient = defaultClient(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    val isConfigured: Boolean =
        baseUrl.startsWith("https://") && anonKey.isNotBlank()

    suspend fun loadWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext DemoWallpapers.items
        }
        retryPolicy.run { attempt ->
            try {
                getList<WallpaperDto>("wallpapers?select=*&order=created_at.desc")
                    .filter { it.storagePath.isNotBlank() }
                    .map { it.toWallpaper(baseUrl) }
            } catch (error: Throwable) {
                throw error.toDannXError()
            }
        }
    }

    suspend fun loadCollections(): List<WallpaperCollection> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext prefs.localCollections()
        }
        val collections = getList<CollectionDto>(
            "collections?select=*&order=created_at.desc",
        )
        if (collections.isEmpty()) {
            return@withContext emptyList()
        }
        val items = getList<CollectionItemDto>(
            "collection_items?select=*&order=position.asc,created_at.asc",
        )
        val byCollection = items.groupBy { it.collectionId }
        collections.map { collection ->
            collection.toCollection(byCollection[collection.id].orEmpty())
        }
    }

    suspend fun signIn(email: String, password: String): AdminSession =
        withContext(Dispatchers.IO) {
            ensureConfigured()
            val auth = authenticate(
                path = "token?grant_type=password",
                payload = json.encodeToString(LoginRequest(email.trim(), password)),
            )
            val resolved = auth.user?.email ?: email.trim()
            prefs.saveSession(
                email = resolved,
                access = auth.accessToken,
                refresh = auth.refreshToken,
                id = auth.user?.id,
            )
            AdminSession(resolved, auth.accessToken, auth.user?.id.orEmpty())
        }

    suspend fun refreshSession(): AdminSession? {
        if (!isConfigured) return null
        val refresh = prefs.refreshToken ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val auth = authenticate(
                    path = "token?grant_type=refresh_token",
                    payload = json.encodeToString(RefreshRequest(refresh)),
                )
                val email = auth.user?.email ?: prefs.adminEmail ?: ""
                val id = auth.user?.id ?: prefs.userId.orEmpty()
                prefs.saveSession(email, auth.accessToken, auth.refreshToken, id)
                AdminSession(email, auth.accessToken, id)
            }.getOrNull()
        }
    }

    /**
     * Sube una imagen sin cargarla entera en memoria: el cuerpo de la petición
     * lee del URI en bloques.
     */
    suspend fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        tags: List<String>,
        collectionId: String?,
    ): Wallpaper = withContext(Dispatchers.IO) {
        ensureConfigured()
        val token = requireToken()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: "image/jpeg"
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        if (size > MAX_BYTES) {
            throw DannXError.Unexpected("La imagen supera 25 MB.")
        }
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val fileName = "${UUID.randomUUID()}.$extension"
        uploadObject(token, fileName, uri, mime)
        val author = prefs.adminEmail?.substringBefore('@') ?: "DannX"
        val created = insertWallpaper(
            token = token,
            wallpaper = NewWallpaper(
                title = title.trim().ifBlank { "Sin título" },
                category = category,
                storagePath = fileName,
                author = author,
                width = readDimension(uri, horizontal = true),
                height = readDimension(uri, horizontal = false),
                tags = tags,
            ),
        )
        if (collectionId != null) {
            runCatching { insertCollectionItem(token, collectionId, created.id) }
        }
        created.toWallpaper(baseUrl)
    }

    suspend fun createCollection(
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection = withContext(Dispatchers.IO) {
        ensureConfigured()
        val token = requireToken()
        val payload = NewCollection(
            name = name.trim(),
            description = description.trim(),
            ownerName = prefs.adminEmail?.substringBefore('@') ?: "DannX",
            isPublic = isPublic,
        )
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/collections")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
                    .build()
            },
        )
        json.decodeFromString<List<CollectionDto>>(body).firstOrNull()
            ?.toCollection(emptyList())
            ?: throw DannXError.Parse("Supabase no devolvió la colección creada.")
    }

    suspend fun addToCollection(collectionId: String, wallpaperId: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            insertCollectionItem(requireToken(), collectionId, wallpaperId)
        }
    }

    suspend fun removeFromCollection(collectionId: String, wallpaperId: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val token = requireToken()
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url(
                            "$baseUrl/rest/v1/collection_items" +
                                "?collection_id=eq.$collectionId&wallpaper_id=eq.$wallpaperId",
                        )
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .delete()
                        .build()
                },
            )
        }
    }

    suspend fun deleteCollection(id: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val token = requireToken()
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url("$baseUrl/rest/v1/collections?id=eq.$id")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .delete()
                        .build()
                },
            )
        }
    }

    suspend fun incrementDownloads(id: String) {
        if (!isConfigured || id.startsWith("demo-")) return
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/increment_downloads")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $anonKey")
                    .post(
                        json.encodeToString(DownloadRequest(id))
                            .toRequestBody(JSON_MEDIA),
                    )
                    .build()
                client.newCall(request).execute().use { it.close() }
            }
        }
    }

    /**
     * Petición pública de lectura. No reintenta ante 401 porque no usa sesión.
     */
    private inline fun <reified T> getList(path: String): List<T> {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/$path")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw errorFor(response.code, body)
            }
            try {
                json.decodeFromString<List<T>>(body)
            } catch (error: Throwable) {
                throw DannXError.Parse(error.message.orEmpty())
            }
        }
    }

    private fun authenticate(path: String, payload: String): AuthResponse {
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/$path")
            .header("apikey", anonKey)
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw errorFor(response.code, body)
            }
            try {
                json.decodeFromString<AuthResponse>(body)
            } catch (error: Throwable) {
                throw DannXError.Parse(error.message.orEmpty())
            }
        }
    }

    /**
     * Ejecuta una petición autenticada. Si el servidor responde 401, renueva el
     * token una sola vez y reintenta; si vuelve a fallar, la sesión venció.
     */
    private suspend fun executeAuthorized(
        allowConflict: Boolean = false,
        build: (String) -> Request,
    ): String {
        val currentToken = prefs.accessToken ?: throw DannXError.SessionExpired
        val first = runRequest(build(currentToken))
        if (first.code != 401) {
            return first.readBody(allowConflict)
        }
        val renewed = refreshSession()
        if (renewed == null) {
            prefs.clearSession()
            throw DannXError.SessionExpired
        }
        val second = runRequest(build(renewed.token))
        if (second.code == 401) {
            prefs.clearSession()
            throw DannXError.SessionExpired
        }
        return second.readBody(allowConflict)
    }

    private fun runRequest(request: Request): HttpResult =
        client.newCall(request).execute().use { response ->
            HttpResult(response.code, response.body?.string().orEmpty())
        }

    private fun HttpResult.readBody(allowConflict: Boolean = false): String {
        if (code == 401) {
            throw DannXError.SessionExpired
        }
        if (code == 409 && allowConflict) {
            return body
        }
        if (code !in 200..299) {
            throw errorFor(code, body)
        }
        return body
    }

    private suspend fun uploadObject(
        token: String,
        fileName: String,
        uri: Uri,
        mime: String,
    ) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                StreamingRequestBody(context, uri, mime),
            )
            .build()
        executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/storage/v1/object/wallpapers/$fileName")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("x-upsert", "false")
                    .post(body)
                    .build()
            },
        )
    }

    private suspend fun insertWallpaper(token: String, wallpaper: NewWallpaper): WallpaperDto {
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/wallpapers")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .post(json.encodeToString(wallpaper).toRequestBody(JSON_MEDIA))
                    .build()
            },
        )
        return json.decodeFromString<List<WallpaperDto>>(body).firstOrNull()
            ?: throw DannXError.Parse("Supabase no devolvió el wallpaper creado.")
    }

    private suspend fun insertCollectionItem(
        token: String,
        collectionId: String,
        wallpaperId: String,
    ) {
        val payload = NewCollectionItem(
            collectionId = collectionId,
            wallpaperId = wallpaperId,
        )
        executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/collection_items")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                    .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
                    .build()
            },
            allowConflict = true,
        )
    }

    private fun requireToken(): String =
        prefs.accessToken ?: throw DannXError.SessionExpired

    private fun ensureConfigured() {
        if (!isConfigured) {
            throw DannXError.Unexpected("Falta conectar Supabase en la configuración del proyecto.")
        }
    }

    private fun errorFor(code: Int, body: String): DannXError = when (code) {
        401, 403 -> DannXError.SessionExpired
        in 500..599 -> DannXError.Server(code)
        else -> DannXError.Unexpected(
            "El servidor respondió $code${if (body.isBlank()) "" else ": ${body.take(120)}"}",
        )
    }

    private fun readDimension(uri: Uri, horizontal: Boolean): Int {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
        }
        return if (horizontal) options.outWidth.coerceAtLeast(0) else options.outHeight.coerceAtLeast(0)
    }

    private data class HttpResult(val code: Int, val body: String)

    companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        val JSON_MEDIA: MediaType = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * Cuerpo de la petición que escribe la imagen desde el URI en bloques, sin
 * cargarla entera en memoria.
 */
private class StreamingRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val mime: String,
) : RequestBody() {
    override fun contentType(): MediaType? = mime.toMediaTypeOrNull()

    override fun contentLength(): Long =
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir la imagen.")
        input.use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
            }
        }
    }
}

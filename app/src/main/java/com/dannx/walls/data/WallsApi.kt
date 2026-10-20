package com.dannx.walls.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.dannx.walls.BuildConfig
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class WallsApi(
    private val context: Context,
    private val prefs: Prefs,
) {
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    val isConfigured: Boolean =
        baseUrl.startsWith("https://") && anonKey.isNotBlank()

    suspend fun loadWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext DemoWallpapers.items
        }
        getList<WallpaperDto>("wallpapers?select=*&order=created_at.desc")
            .filter { it.storagePath.isNotBlank() }
            .map { it.toWallpaper(baseUrl) }
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
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("No se pudo abrir la imagen.")
        if (bytes.size > MAX_BYTES) {
            throw IOException("La imagen supera 25 MB.")
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val fileName = "${UUID.randomUUID()}.$extension"
        uploadObject(token, fileName, bytes, mime)
        val author = prefs.adminEmail?.substringBefore('@') ?: "DannX"
        val created = insertWallpaper(
            token = token,
            wallpaper = NewWallpaper(
                title = title.trim().ifBlank { "Sin título" },
                category = category,
                storagePath = fileName,
                author = author,
                width = options.outWidth.coerceAtLeast(0),
                height = options.outHeight.coerceAtLeast(0),
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
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/collections")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "return=representation")
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(serverError(response.code, body))
            }
            json.decodeFromString<List<CollectionDto>>(body).firstOrNull()
                ?.toCollection(emptyList())
                ?: throw IOException("Supabase no devolvió la colección creada.")
        }
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
            val request = Request.Builder()
                .url(
                    "$baseUrl/rest/v1/collection_items" +
                        "?collection_id=eq.$collectionId&wallpaper_id=eq.$wallpaperId",
                )
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException(serverError(response.code, body))
                }
            }
        }
    }

    suspend fun deleteCollection(id: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val token = requireToken()
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/collections?id=eq.$id")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException(serverError(response.code, body))
                }
            }
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
                throw IOException(serverError(response.code, body))
            }
            json.decodeFromString<List<T>>(body)
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
                throw IOException(serverError(response.code, body))
            }
            json.decodeFromString<AuthResponse>(body)
        }
    }

    private fun uploadObject(
        token: String,
        fileName: String,
        bytes: ByteArray,
        mime: String,
    ) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody(mime.toMediaTypeOrNull()),
            )
            .build()
        val request = Request.Builder()
            .url("$baseUrl/storage/v1/object/wallpapers/$fileName")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("x-upsert", "false")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(serverError(response.code, bodyText))
            }
        }
    }

    private fun insertWallpaper(token: String, wallpaper: NewWallpaper): WallpaperDto {
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/wallpapers")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "return=representation")
            .post(json.encodeToString(wallpaper).toRequestBody(JSON_MEDIA))
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(serverError(response.code, body))
            }
            json.decodeFromString<List<WallpaperDto>>(body).firstOrNull()
                ?: throw IOException("Supabase no devolvió el wallpaper creado.")
        }
    }

    private fun insertCollectionItem(
        token: String,
        collectionId: String,
        wallpaperId: String,
    ) {
        val payload = NewCollectionItem(
            collectionId = collectionId,
            wallpaperId = wallpaperId,
        )
        val request = Request.Builder()
            .url("$baseUrl/rest/v1/collection_items")
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $token")
            .header("Prefer", "resolution=ignore-duplicates,return=minimal")
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 409) {
                val body = response.body?.string().orEmpty()
                throw IOException(serverError(response.code, body))
            }
        }
    }

    private fun requireToken(): String =
        prefs.accessToken ?: throw IOException("Tu sesión expiró. Vuelve a entrar.")

    private fun ensureConfigured() {
        if (!isConfigured) {
            throw IOException("Falta conectar Supabase en la configuración del proyecto.")
        }
    }

    private fun serverError(code: Int, body: String): String {
        val detail = body.take(240).ifBlank { "sin detalle" }
        return "Supabase respondió $code: $detail"
    }

    private companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
        val JSON_MEDIA = "application/json".toMediaType()
    }
}

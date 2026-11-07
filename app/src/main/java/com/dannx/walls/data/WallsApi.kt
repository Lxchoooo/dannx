package com.dannx.walls.data

import android.content.Context
import android.net.Uri
import com.dannx.walls.BuildConfig
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class WallsApi(
    private val context: Context,
    private val prefs: Prefs,
    private val client: OkHttpClient = defaultClient(),
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) : WallpapersRepository {
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    override val isConfigured: Boolean =
        baseUrl.startsWith("https://") && anonKey.isNotBlank()

    override suspend fun loadWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
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

    override suspend fun loadCollections(): List<WallpaperCollection> = withContext(Dispatchers.IO) {
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

    override suspend fun signIn(email: String, password: String): AdminSession =
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

    override suspend fun refreshSession(): AdminSession? {
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
     * Sube una imagen normalizada: si el formato no está permitido (por ejemplo
     * HEIC) o pesa más de 25 MB, se convierte a JPEG antes de enviarla.
     */
    override suspend fun uploadWallpaper(
        uri: Uri,
        title: String,
        category: String,
        tags: List<String>,
        collectionId: String?,
    ): Wallpaper = withContext(Dispatchers.IO) {
        ensureConfigured()
        val token = requireToken()
        val prepared = prepareUploadImage(context, uri, MAX_BYTES)
        val fileName = "${UUID.randomUUID()}.${prepared.extension}"
        try {
            uploadObject(token, fileName, prepared)
            val author = prefs.adminEmail?.substringBefore('@') ?: "DannX"
            val created = try {
                insertWallpaper(
                    token = token,
                    wallpaper = NewWallpaper(
                        title = title.trim().ifBlank { "Sin título" }.take(120),
                        category = category.trim().take(40).ifBlank { "Abstracto" },
                        storagePath = fileName,
                        author = author.take(60),
                        width = prepared.width,
                        height = prepared.height,
                        tags = tags.map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .take(12),
                    ),
                )
            } catch (error: Throwable) {
                // El archivo ya está arriba pero la ficha falló: se borra para
                // no dejar basura en el almacenamiento.
                runCatching { deleteObject(token, fileName) }
                throw error
            }
            if (collectionId != null) {
                runCatching { insertCollectionItem(token, collectionId, created.id) }
            }
            created.toWallpaper(baseUrl)
        } finally {
            prepared.file.delete()
        }
    }

    override suspend fun createCollection(
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

    override suspend fun addToCollection(collectionId: String, wallpaperId: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            insertCollectionItem(requireToken(), collectionId, wallpaperId)
        }
    }

    override suspend fun removeFromCollection(collectionId: String, wallpaperId: String) {
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

    override suspend fun deleteCollection(id: String) {
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
        prepared: PreparedImage,
    ) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                prepared.file.asRequestBody(prepared.mime.toMediaType()),
            )
            .build()
        try {
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
        } catch (error: Throwable) {
            throw error.uploadFriendly()
        }
    }

    private suspend fun deleteObject(token: String, fileName: String) {
        executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/storage/v1/object/wallpapers/$fileName")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
            },
        )
    }

    /**
     * Traduce los errores más comunes del almacenamiento a algo que se entienda.
     */
    private fun Throwable.uploadFriendly(): Throwable {
        val detail = message.orEmpty()
        return when {
            this is DannXError.Forbidden -> DannXError.Unexpected(
                "Tu cuenta no tiene permiso de administrador para subir archivos.",
            )

            this is DannXError.Unexpected && detail.contains("mime type", ignoreCase = true) ->
                DannXError.Unexpected(
                    "El formato de la imagen no está permitido. Probá con JPG, PNG o WebP.",
                )

            this is DannXError.Unexpected && detail.contains("maximum", ignoreCase = true) ->
                DannXError.Unexpected("La imagen supera el límite de 25 MB.")

            this is DannXError.Unexpected && detail.contains("bucket", ignoreCase = true) ->
                DannXError.Unexpected(
                    "Falta crear el almacenamiento «wallpapers» en Supabase.",
                )

            else -> this
        }
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
        401 -> DannXError.SessionExpired
        403 -> DannXError.Forbidden
        in 500..599 -> DannXError.Server(code)
        else -> DannXError.Unexpected(
            "El servidor respondió $code${if (body.isBlank()) "" else ": ${body.take(120)}"}",
        )
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

package com.dannx.walls.data

import android.content.Context
import android.net.Uri
import com.dannx.walls.BuildConfig
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        encodeDefaults = true
        coerceInputValues = true
    }
    private val refreshMutex = Mutex()

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
                    .filterNot { it.isArchived }
            } catch (error: Throwable) {
                throw error.toDannXError()
            }
        }
    }

    override suspend fun loadAdminWallpapers(): List<Wallpaper> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext DemoWallpapers.items
        }
        retryPolicy.run { attempt ->
            try {
                adminWallpaperDtos()
                    .filter { it.storagePath.isNotBlank() }
                    .map { it.toWallpaper(baseUrl) }
            } catch (error: Throwable) {
                throw error.toDannXError()
            }
        }
    }

    override suspend fun loadCategories(): List<WallpaperCategory> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext defaultCategories()
        }
        getList<CategoryDto>("categories?select=*&order=position.asc,name.asc")
            .map { it.toCategory() }
    }

    override suspend fun loadCollections(): List<WallpaperCollection> = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext prefs.localCollections()
        }
        // Con sesión se leen también las colecciones privadas del usuario. Sin
        // sesión se mantiene la lectura pública anónima.
        val authenticated = prefs.accessToken != null
        val collections = if (authenticated) {
            getListAuthorized<CollectionDto>("collections?select=*&order=created_at.desc")
        } else {
            getList<CollectionDto>("collections?select=*&order=created_at.desc")
        }
        if (collections.isEmpty()) {
            return@withContext emptyList()
        }
        val items = if (authenticated) {
            getListAuthorized<CollectionItemDto>(
                "collection_items?select=*&order=position.asc,created_at.asc",
            )
        } else {
            getList<CollectionItemDto>(
                "collection_items?select=*&order=position.asc,created_at.asc",
            )
        }
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

    override suspend fun refreshSession(): AdminSession? =
        refreshMutex.withLock { refreshSessionOnce() }

    /**
     * Renueva el token solo si nadie lo renovó ya. Dos respuestas 401
     * simultáneas comparten una única rotación del refresh token.
     */
    private suspend fun renewSession(failedToken: String): AdminSession? =
        refreshMutex.withLock {
            val current = prefs.accessToken
            if (current != null && current != failedToken) {
                return@withLock AdminSession(
                    email = prefs.adminEmail.orEmpty(),
                    token = current,
                    userId = prefs.userId.orEmpty(),
                )
            }
            refreshSessionOnce()
        }

    private suspend fun refreshSessionOnce(): AdminSession? {
        if (!isConfigured) return null
        val refresh = prefs.refreshToken ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val auth = authenticate(
                    path = "token?grant_type=refresh_token",
                    payload = json.encodeToString(RefreshRequest(refresh)),
                )
                val email = auth.user?.email ?: prefs.adminEmail ?: ""
                val id = auth.user?.id ?: prefs.userId.orEmpty()
                prefs.saveSession(email, auth.accessToken, auth.refreshToken, id)
                AdminSession(email, auth.accessToken, id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: DannXError) {
                // Un rechazo del refresh token puede llegar como 400/401/403.
                // Los errores de red o del servidor no borran la sesión.
                if (error !is DannXError.Offline && error !is DannXError.Server) {
                    prefs.clearSession()
                }
                null
            } catch (_: Exception) {
                null
            }
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
        categoryId: String?,
        tags: List<String>,
        collectionId: String?,
    ): UploadOutcome = withContext(Dispatchers.IO) {
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
                        categoryId = categoryId,
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
                throw if (error.message.orEmpty().contains("Could not find the table")) {
                    DannXError.Unexpected("Falta crear las tablas del catálogo.")
                } else {
                    error
                }
            }
            val warning = if (collectionId != null) {
                try {
                    insertCollectionItem(token, collectionId, created.id)
                    null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: DannXError.SessionExpired) {
                    // La ficha ya está publicada; se propaga para que la app
                    // vuelva a pedir sesión y no muestre un falso éxito.
                    throw error
                } catch (error: Exception) {
                    "El fondo se publicó, pero no se pudo agregar a la colección."
                }
            } else {
                null
            }
            UploadOutcome(
                wallpaper = created.toWallpaper(baseUrl),
                warning = warning,
            )
        } finally {
            prepared.file.delete()
        }
    }

    override suspend fun updateWallpaper(
        wallpaperId: String,
        title: String,
        category: String,
        categoryId: String?,
        tags: List<String>,
        featured: Boolean,
    ): Wallpaper = withContext(Dispatchers.IO) {
        ensureConfigured()
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/wallpapers?id=eq.$wallpaperId")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .patch(
                        json.encodeToString(
                            WallpaperUpdate(
                                title = title.trim().ifBlank { "Sin título" }.take(120),
                                category = category.trim().take(40).ifBlank { "Abstracto" },
                                categoryId = categoryId,
                                tags = cleanTags(tags),
                                featured = featured,
                            ),
                        ).toRequestBody(JSON_MEDIA),
                    )
                    .build()
            },
        )
        json.decodeFromString<List<WallpaperDto>>(body).firstOrNull()
            ?.toWallpaper(baseUrl)
            ?: throw DannXError.Parse("El servidor no devolvió el fondo actualizado.")
    }

    override suspend fun replaceWallpaperImage(
        wallpaper: Wallpaper,
        uri: Uri,
    ): Wallpaper = withContext(Dispatchers.IO) {
        ensureConfigured()
        val token = requireToken()
        val prepared = prepareUploadImage(context, uri, MAX_BYTES)
        val fileName = "${UUID.randomUUID()}.${prepared.extension}"
        val oldPath = wallpaper.storagePath.ifBlank {
            wallpaper.imageUrl.substringAfter("/object/public/wallpapers/", "")
        }
        try {
            uploadObject(token, fileName, prepared)
            val body = executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url("$baseUrl/rest/v1/wallpapers?id=eq.${wallpaper.id}")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Prefer", "return=representation")
                        .patch(
                            json.encodeToString(
                                WallpaperImageUpdate(
                                    storagePath = fileName,
                                    width = prepared.width,
                                    height = prepared.height,
                                ),
                            ).toRequestBody(JSON_MEDIA),
                        )
                        .build()
                },
            )
            val updated = json.decodeFromString<List<WallpaperDto>>(body)
                .firstOrNull()
                ?.toWallpaper(baseUrl)
                ?: throw DannXError.Parse("El servidor no confirmó la nueva imagen.")
            // La imagen anterior solo se borra cuando la ficha ya apunta a la
            // nueva. Si el PATCH no confirmó, la ficha sigue usando la vieja.
            if (oldPath.isNotBlank() && oldPath != fileName) {
                runCatching { deleteObject(token, oldPath) }
            }
            updated
        } catch (error: Throwable) {
            runCatching { deleteObject(token, fileName) }
            throw error
        } finally {
            prepared.file.delete()
        }
    }

    override suspend fun archiveWallpaper(wallpaperId: String, archived: Boolean) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val payload = if (archived) {
                """{"archived_at":"${Instant.now()}"}"""
            } else {
                """{"archived_at":null}"""
            }
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url("$baseUrl/rest/v1/wallpapers?id=eq.$wallpaperId")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Prefer", "return=minimal")
                        .patch(payload.toRequestBody(JSON_MEDIA))
                        .build()
                },
            )
        }
    }

    override suspend fun deleteWallpaper(wallpaper: Wallpaper) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val token = requireToken()
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url("$baseUrl/rest/v1/wallpapers?id=eq.${wallpaper.id}")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .delete()
                        .build()
                },
            )
            val path = wallpaper.storagePath.ifBlank {
                wallpaper.imageUrl.substringAfter("/object/public/wallpapers/", "")
            }
            if (path.isNotBlank()) {
                // La ficha ya no existe; si falla el archivo no se rompe el catálogo.
                runCatching { deleteObject(token, path) }
            }
        }
    }

    override suspend fun createCategory(
        name: String,
        position: Int,
    ): WallpaperCategory = withContext(Dispatchers.IO) {
        ensureConfigured()
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/categories")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .post(
                        json.encodeToString(
                            NewCategory(
                                name = name.trim().take(40),
                                position = position,
                            ),
                        ).toRequestBody(JSON_MEDIA),
                    )
                    .build()
            },
        )
        json.decodeFromString<List<CategoryDto>>(body).firstOrNull()
            ?.toCategory()
            ?: throw DannXError.Parse("El servidor no devolvió la categoría creada.")
    }

    override suspend fun updateCategory(
        id: String,
        name: String,
        position: Int,
    ): WallpaperCategory = withContext(Dispatchers.IO) {
        ensureConfigured()
        val cleanName = name.trim().take(40)
        try {
            callRpc(
                "update_category",
                json.encodeToString(
                    UpdateCategoryPayload(
                        id = id,
                        name = cleanName,
                        position = position,
                    ),
                ),
            )
            WallpaperCategory(id = id, name = cleanName, position = position)
        } catch (error: DannXError) {
            if (!error.isMissingRpc()) throw error
            updateCategorySequential(id, cleanName, position)
        }
    }

    /**
     * Compatibilidad mientras la migración del Estudio no esté aplicada. La
     * versión con función actualiza la categoría y deja que el trigger
     * sincronice el texto de los fondos en la misma transacción.
     */
    private suspend fun updateCategorySequential(
        id: String,
        name: String,
        position: Int,
    ): WallpaperCategory {
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/categories?id=eq.$id")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .patch(
                        json.encodeToString(
                            CategoryUpdate(name = name, position = position),
                        ).toRequestBody(JSON_MEDIA),
                    )
                    .build()
            },
        )
        executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/wallpapers?category_id=eq.$id")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=minimal")
                    .patch(
                        json.encodeToString(CategoryNameUpdate(name))
                            .toRequestBody(JSON_MEDIA),
                    )
                    .build()
            },
        )
        return json.decodeFromString<List<CategoryDto>>(body).firstOrNull()
            ?.toCategory()
            ?: throw DannXError.Parse("El servidor no devolvió la categoría actualizada.")
    }

    override suspend fun reassignCategory(
        categoryId: String,
        targetId: String,
        targetName: String,
    ) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url("$baseUrl/rest/v1/wallpapers?category_id=eq.$categoryId")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Prefer", "return=minimal")
                        .patch(
                            json.encodeToString(
                                CategoryAssignmentUpdate(
                                    categoryId = targetId,
                                    category = targetName.trim().take(40),
                                ),
                            ).toRequestBody(JSON_MEDIA),
                        )
                        .build()
                },
            )
        }
    }

    override suspend fun deleteCategory(id: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url("$baseUrl/rest/v1/categories?id=eq.$id")
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .delete()
                        .build()
                },
            )
        }
    }

    override suspend fun updateCollection(
        id: String,
        name: String,
        description: String,
        isPublic: Boolean,
    ): WallpaperCollection = withContext(Dispatchers.IO) {
        ensureConfigured()
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/collections?id=eq.$id")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=representation")
                    .patch(
                        json.encodeToString(
                            CollectionUpdate(
                                name = name.trim().take(60),
                                description = description.trim(),
                                isPublic = isPublic,
                            ),
                        ).toRequestBody(JSON_MEDIA),
                    )
                    .build()
            },
        )
        json.decodeFromString<List<CollectionDto>>(body).firstOrNull()
            ?.toCollection(emptyList())
            ?: throw DannXError.Parse("El servidor no devolvió la colección actualizada.")
    }

    override suspend fun reorderCollection(
        collectionId: String,
        wallpaperIds: List<String>,
    ) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val selected = wallpaperIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (selected.isEmpty()) return@withContext
            try {
                callRpc(
                    "reorder_collection",
                    json.encodeToString(
                        ReorderCollectionPayload(
                            collectionId = collectionId,
                            wallpaperIds = selected,
                        ),
                    ),
                )
            } catch (error: DannXError) {
                if (!error.isMissingRpc()) throw error
                reorderCollectionSequential(collectionId, selected)
            }
        }
    }

    /**
     * Compatibilidad mientras la migración del Estudio no esté aplicada.
     */
    private suspend fun reorderCollectionSequential(
        collectionId: String,
        wallpaperIds: List<String>,
    ) {
        wallpaperIds.forEachIndexed { index, wallpaperId ->
            executeAuthorized(
                build = { token ->
                    Request.Builder()
                        .url(
                            "$baseUrl/rest/v1/collection_items" +
                                "?collection_id=eq.$collectionId" +
                                "&wallpaper_id=eq.$wallpaperId",
                        )
                        .header("apikey", anonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Prefer", "return=minimal")
                        .patch(
                            json.encodeToString(PositionUpdate(index))
                                .toRequestBody(JSON_MEDIA),
                        )
                        .build()
                },
            )
        }
    }

    override suspend fun addTag(tag: String, wallpaperIds: List<String>) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val clean = tag.trim().take(40)
            val selected = wallpaperIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            if (clean.isBlank() || selected.isEmpty()) return@withContext
            try {
                callRpc(
                    "add_wallpaper_tag",
                    json.encodeToString(
                        AddTagPayload(tag = clean, wallpaperIds = selected),
                    ),
                )
            } catch (error: DannXError) {
                if (!error.isMissingRpc()) throw error
                addTagSequential(clean, selected)
            }
        }
    }

    override suspend fun renameTag(oldTag: String, newTag: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val old = oldTag.trim()
            val new = newTag.trim().take(40)
            if (old.isBlank() || new.isBlank()) return@withContext
            try {
                callRpc(
                    "rename_wallpaper_tag",
                    json.encodeToString(RenameTagPayload(oldTag = old, newTag = new)),
                )
            } catch (error: DannXError) {
                if (!error.isMissingRpc()) throw error
                renameTagSequential(old, new)
            }
        }
    }

    override suspend fun deleteTag(tag: String) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val clean = tag.trim()
            if (clean.isBlank()) return@withContext
            try {
                callRpc(
                    "delete_wallpaper_tag",
                    json.encodeToString(DeleteTagPayload(tag = clean)),
                )
            } catch (error: DannXError) {
                if (!error.isMissingRpc()) throw error
                deleteTagSequential(clean)
            }
        }
    }

    /**
     * Compatibilidad mientras la migración del Estudio no esté aplicada.
     */
    private suspend fun addTagSequential(tag: String, wallpaperIds: List<String>) {
        val token = requireToken()
        val selected = wallpaperIds.toSet()
        adminWallpaperDtos()
            .filter { it.id in selected }
            .forEach { dto ->
                val tags = cleanTags(dto.tags + tag)
                if (tags != dto.tags) updateTags(token, dto.id, tags)
            }
    }

    /**
     * Compatibilidad mientras la migración del Estudio no esté aplicada.
     */
    private suspend fun renameTagSequential(oldTag: String, newTag: String) {
        val token = requireToken()
        adminWallpaperDtos().forEach { dto ->
            if (dto.tags.none { it.equals(oldTag, ignoreCase = true) }) {
                return@forEach
            }
            val tags = cleanTags(
                dto.tags.map { if (it.equals(oldTag, ignoreCase = true)) newTag else it },
            )
            updateTags(token, dto.id, tags)
        }
    }

    /**
     * Compatibilidad mientras la migración del Estudio no esté aplicada.
     */
    private suspend fun deleteTagSequential(tag: String) {
        val token = requireToken()
        adminWallpaperDtos().forEach { dto ->
            if (dto.tags.none { it.equals(tag, ignoreCase = true) }) {
                return@forEach
            }
            val tags = dto.tags.filterNot { it.equals(tag, ignoreCase = true) }
            updateTags(token, dto.id, tags)
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
            ?: throw DannXError.Parse("El servidor no devolvió la colección creada.")
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

    override suspend fun setCollectionWallpapers(
        collectionId: String,
        wallpaperIds: List<String>,
    ) {
        if (!isConfigured) return
        withContext(Dispatchers.IO) {
            val selected = wallpaperIds
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val current = getListAuthorized<CollectionItemRef>(
                "collection_items?select=wallpaper_id&collection_id=eq.$collectionId",
            ).map { it.wallpaperId }.toSet()
            val toAdd = selected.filterNot { it in current }
            val toRemove = current.filterNot { it in selected }

            if (toAdd.isNotEmpty()) {
                val start = current.size
                val payload = toAdd.mapIndexed { index, id ->
                    NewCollectionItem(
                        collectionId = collectionId,
                        wallpaperId = id,
                        position = start + index,
                    )
                }
                executeAuthorized(
                    build = { token ->
                        Request.Builder()
                            .url("$baseUrl/rest/v1/collection_items")
                            .header("apikey", anonKey)
                            .header("Authorization", "Bearer $token")
                            .header(
                                "Prefer",
                                "resolution=ignore-duplicates,return=minimal",
                            )
                            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA))
                            .build()
                    },
                )
            }

            if (toRemove.isNotEmpty()) {
                executeAuthorized(
                    build = { token ->
                        Request.Builder()
                            .url(
                                "$baseUrl/rest/v1/collection_items" +
                                    "?collection_id=eq.$collectionId" +
                                    "&wallpaper_id=in.(${toRemove.joinToString(",")})",
                            )
                            .header("apikey", anonKey)
                            .header("Authorization", "Bearer $token")
                            .delete()
                            .build()
                    },
                )
            }
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

    private suspend fun adminWallpaperDtos(): List<WallpaperDto> {
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/wallpapers?select=*&order=created_at.desc")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            },
        )
        return json.decodeFromString<List<WallpaperDto>>(body)
    }

    private suspend fun updateTags(token: String, wallpaperId: String, tags: List<String>) {
        executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/wallpapers?id=eq.$wallpaperId")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=minimal")
                    .patch(
                        json.encodeToString(TagsUpdate(tags)).toRequestBody(JSON_MEDIA),
                    )
                    .build()
            },
        )
    }

    /**
     * Llama a una función de base de datos. Si la migración todavía no se
     * aplicó, PostgREST responde PGRST202 y quien llama puede usar el
     * recorrido de compatibilidad.
     */
    private suspend fun callRpc(name: String, payload: String): String =
        executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/$name")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Prefer", "return=minimal")
                    .post(payload.toRequestBody(JSON_MEDIA))
                    .build()
            },
        )

    private fun DannXError.isMissingRpc(): Boolean = when (this) {
        is DannXError.Unexpected ->
            causeDetail.contains("PGRST202", ignoreCase = true) ||
                causeDetail.contains("Could not find the function", ignoreCase = true) ||
                causeDetail.contains("no existe la función", ignoreCase = true) ||
                causeDetail.contains("respondió 404", ignoreCase = true)
        else -> false
    }

    private fun cleanTags(tags: List<String>): List<String> = tags
        .map { it.trim().take(40) }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .take(12)

    private fun defaultCategories(): List<WallpaperCategory> =
        DefaultCategories.mapIndexed { index, name ->
            WallpaperCategory(id = "demo-category-$index", name = name, position = index)
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
            decodeList(body)
        }
    }

    /**
     * Lectura con el token del usuario. Renueva la sesión una vez si venció.
     */
    private suspend inline fun <reified T> getListAuthorized(path: String): List<T> {
        val body = executeAuthorized(
            build = { token ->
                Request.Builder()
                    .url("$baseUrl/rest/v1/$path")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .get()
                    .build()
            },
        )
        return decodeList(body)
    }

    private inline fun <reified T> decodeList(body: String): List<T> =
        try {
            json.decodeFromString<List<T>>(body)
        } catch (error: Throwable) {
            throw DannXError.Parse(error.message.orEmpty())
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
        val renewed = renewSession(currentToken)
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

            this is DannXError.Unexpected &&
                detail.contains("bucket not found", ignoreCase = true) ->
                DannXError.Unexpected(
                    "Falta crear el almacenamiento «wallpapers» del catálogo.",
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
            ?: throw DannXError.Parse("El servidor no devolvió el fondo creado.")
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
            throw DannXError.Unexpected("Falta conectar el catálogo en la configuración del proyecto.")
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

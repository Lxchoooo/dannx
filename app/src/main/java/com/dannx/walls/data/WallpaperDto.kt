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
    val tags: List<String> = emptyList(),
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("archived_at") val archivedAt: String? = null,
    val categories: CategoryRefDto? = null,
)

@Serializable
data class CategoryRefDto(
    val name: String = "",
)

@Serializable
data class NewWallpaper(
    val title: String,
    val category: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("storage_path") val storagePath: String,
    val author: String,
    val width: Int,
    val height: Int,
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
)

@Serializable
data class WallpaperUpdate(
    val title: String,
    val category: String,
    @SerialName("category_id") val categoryId: String? = null,
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String = "",
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class NewCategory(
    val name: String,
    val position: Int = 0,
)

@Serializable
data class CollectionUpdate(
    val name: String,
    val description: String,
    @SerialName("is_public") val isPublic: Boolean,
)

@Serializable
data class WallpaperImageUpdate(
    @SerialName("storage_path") val storagePath: String,
    val width: Int,
    val height: Int,
)

@Serializable
data class CategoryUpdate(
    val name: String,
    val position: Int,
)

@Serializable
data class CategoryNameUpdate(
    val category: String,
)

@Serializable
data class CategoryAssignmentUpdate(
    @SerialName("category_id") val categoryId: String,
    val category: String,
)

@Serializable
data class PositionUpdate(
    val position: Int,
)

@Serializable
data class TagsUpdate(
    val tags: List<String>,
)

@Serializable
data class AddTagPayload(
    @SerialName("p_tag") val tag: String,
    @SerialName("p_wallpaper_ids") val wallpaperIds: List<String>,
)

@Serializable
data class RenameTagPayload(
    @SerialName("p_old_tag") val oldTag: String,
    @SerialName("p_new_tag") val newTag: String,
)

@Serializable
data class DeleteTagPayload(
    @SerialName("p_tag") val tag: String,
)

@Serializable
data class ReorderCollectionPayload(
    @SerialName("p_collection_id") val collectionId: String,
    @SerialName("p_wallpaper_ids") val wallpaperIds: List<String>,
)

@Serializable
data class UpdateCategoryPayload(
    @SerialName("p_id") val id: String,
    @SerialName("p_name") val name: String,
    @SerialName("p_position") val position: Int,
)

@Serializable
data class CollectionDto(
    val id: String,
    val name: String = "",
    val description: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class CollectionItemDto(
    @SerialName("collection_id") val collectionId: String,
    @SerialName("wallpaper_id") val wallpaperId: String,
    val position: Int = 0,
)

@Serializable
data class NewCollection(
    val name: String,
    val description: String = "",
    @SerialName("owner_name") val ownerName: String,
    @SerialName("is_public") val isPublic: Boolean = true,
)

@Serializable
data class NewCollectionItem(
    @SerialName("collection_id") val collectionId: String,
    @SerialName("wallpaper_id") val wallpaperId: String,
    val position: Int = 0,
)

@Serializable
data class CollectionItemRef(
    @SerialName("wallpaper_id") val wallpaperId: String,
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

fun WallpaperDto.toWallpaper(baseUrl: String): Wallpaper = Wallpaper(
    id = id,
    title = title.ifBlank { "Sin título" },
    category = categories?.name?.takeIf { it.isNotBlank() }
        ?: category.ifBlank { "Abstracto" },
    imageUrl = "$baseUrl/storage/v1/object/public/wallpapers/$storagePath",
    author = author.ifBlank { "DannX" },
    width = width,
    height = height,
    featured = featured,
    createdAt = createdAt,
    tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
    categoryId = categoryId,
    storagePath = storagePath,
    createdBy = createdBy.orEmpty(),
    updatedAt = updatedAt,
    archivedAt = archivedAt.orEmpty(),
)

fun CategoryDto.toCategory(): WallpaperCategory = WallpaperCategory(
    id = id,
    name = name.ifBlank { "Sin categoría" },
    position = position,
)

fun CollectionDto.toCollection(items: List<CollectionItemDto>): WallpaperCollection =
    WallpaperCollection(
        id = id,
        name = name.ifBlank { "Sin título" },
        description = description,
        ownerId = ownerId,
        ownerName = ownerName.ifBlank { "DannX" },
        isPublic = isPublic,
        createdAt = createdAt,
        wallpaperIds = items
            .sortedBy { it.position }
            .map { it.wallpaperId },
    )

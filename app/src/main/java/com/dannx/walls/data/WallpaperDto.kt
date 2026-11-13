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
    @SerialName("featured_position") val featuredPosition: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("updated_at") val updatedAt: String = "",
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
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
    val author: String,
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
    @SerialName("featured_position") val featuredPosition: Int = 0,
)

@Serializable
data class FeaturedPositionUpdate(
    @SerialName("featured_position") val featuredPosition: Int,
)

@Serializable
data class AppSettingDto(
    val key: String,
    val value: String = "",
)

@Serializable
data class AppSettingUpdate(
    val key: String,
    val value: String,
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
data class TagCategoryDto(
    val tag: String = "",
    @SerialName("display_tag") val displayTag: String = "",
    @SerialName("category_id") val categoryId: String = "",
    val categories: CategoryRefDto? = null,
)

@Serializable
data class SetTagCategoryPayload(
    @SerialName("p_tags") val tags: List<String>,
    @SerialName("p_category_id") val categoryId: String,
)

@Serializable
data class DeleteTagCategoryPayload(
    @SerialName("p_tag") val tag: String,
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
data class DeleteCategoryPayload(
    @SerialName("p_category_id") val categoryId: String,
    @SerialName("p_target_id") val targetId: String? = null,
)

@Serializable
data class BulkFeaturedPayload(
    @SerialName("p_ids") val ids: List<String>,
    @SerialName("p_featured") val featured: Boolean,
)

@Serializable
data class BulkArchivedPayload(
    @SerialName("p_ids") val ids: List<String>,
    @SerialName("p_archived") val archived: Boolean,
)

@Serializable
data class BulkCategoryPayload(
    @SerialName("p_ids") val ids: List<String>,
    @SerialName("p_category_id") val categoryId: String,
)

@Serializable
data class BulkIdsPayload(
    @SerialName("p_ids") val ids: List<String>,
)

@Serializable
data class ReorderFeaturedPayload(
    @SerialName("p_ids") val ids: List<String>,
)

@Serializable
data class FeaturedPatch(
    val featured: Boolean,
    @SerialName("featured_position") val featuredPosition: Int,
)

@Serializable
data class ArchivedPatch(
    @SerialName("archived_at") val archivedAt: String?,
)

@Serializable
data class DeletedPatch(
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("deleted_by") val deletedBy: String?,
)

@Serializable
data class CategoryPatch(
    @SerialName("category_id") val categoryId: String,
)

@Serializable
data class CollectionDto(
    val id: String,
    val name: String = "",
    val description: String = "",
    @SerialName("owner_id") val ownerId: String = "",
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("is_public") val isPublic: Boolean = false,
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
    @SerialName("is_public") val isPublic: Boolean = false,
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
    featuredPosition = featuredPosition,
    createdAt = createdAt,
    tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
    categoryId = categoryId,
    storagePath = storagePath,
    createdBy = createdBy.orEmpty(),
    updatedAt = updatedAt,
    archivedAt = archivedAt.orEmpty(),
    deletedAt = deletedAt.orEmpty(),
)

fun List<AppSettingDto>.toAppSettings(): AppSettings {
    val values = associate { it.key to it.value }
    return AppSettings(
        tagline = values[AppSettingsKeyTagline].orEmpty(),
        bannerText = values[AppSettingsKeyBannerText].orEmpty(),
        bannerEnabled = values[AppSettingsKeyBannerEnabled].toBoolean(),
    )
}

fun AppSettings.toAppSettingRows(): List<AppSettingUpdate> = listOf(
    AppSettingUpdate(AppSettingsKeyTagline, tagline),
    AppSettingUpdate(AppSettingsKeyBannerText, bannerText),
    AppSettingUpdate(AppSettingsKeyBannerEnabled, bannerEnabled.toString()),
)

const val AppSettingsKeyTagline = "home_tagline"
const val AppSettingsKeyBannerText = "home_banner_text"
const val AppSettingsKeyBannerEnabled = "home_banner_enabled"

fun CategoryDto.toCategory(): WallpaperCategory = WallpaperCategory(
    id = id,
    name = name.ifBlank { "Sin categoría" },
    position = position,
)

fun TagCategoryDto.toTagCategory(): TagCategory = TagCategory(
    tag = tag,
    displayTag = displayTag.ifBlank { tag },
    categoryId = categoryId,
    categoryName = categories?.name.orEmpty(),
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

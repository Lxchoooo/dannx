package com.dannx.walls.ui.screens

import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.DefaultCategories
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.parseTags

private enum class StudioSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    PUBLISH(R.string.studio_section_publish, Icons.Rounded.AddPhotoAlternate),
    WALLS(R.string.studio_my_walls, Icons.Rounded.Image),
    TRASH(R.string.studio_trash, Icons.Rounded.Delete),
    CATEGORIES(R.string.studio_categories, Icons.Rounded.Category),
    TAGS(R.string.studio_section_tags, Icons.Rounded.LocalOffer),
    COLLECTIONS(R.string.studio_collections, Icons.Rounded.Folder),
    CONTENT(R.string.studio_content, Icons.Rounded.Edit),
    FEATURED(R.string.studio_featured_order, Icons.Rounded.Star),
}

private enum class StudioWallSort(@StringRes val labelRes: Int) {
    RECENT(R.string.studio_sort_recent),
    TITLE(R.string.studio_sort_title),
    CATEGORY(R.string.studio_sort_category),
}

@Composable
fun StudioScreen(
    state: UiState,
    onSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onUpload: (Uri, String, String, String?, List<String>, String?) -> Unit,
    onRefresh: () -> Unit,
    onSelectUploadUri: (Uri?) -> Unit = {},
    onBack: () -> Unit = {},
    onLoadStudio: () -> Unit = {},
    onCreateCategory: (String) -> Unit = {},
    onUpdateCategory: (String, String) -> Unit = { _, _ -> },
    onMoveCategory: (String, Int) -> Unit = { _, _ -> },
    onDeleteCategory: (String, String?) -> Unit = { _, _ -> },
    onUpdateWallpaper: (String, String, String, String?, String, List<String>, Boolean) -> Unit =
        { _, _, _, _, _, _, _ -> },
    onReplaceImage: (String, Uri) -> Unit = { _, _ -> },
    onArchiveWallpaper: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteWallpaper: (String) -> Unit = {},
    onRestoreWallpapers: (Set<String>) -> Unit = {},
    onPurgeWallpaper: (String) -> Unit = {},
    onEmptyTrash: () -> Unit = {},
    onBulkSetFeatured: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onBulkSetArchived: (Set<String>, Boolean) -> Unit = { _, _ -> },
    onBulkDeleteWallpapers: (Set<String>) -> Unit = {},
    onBulkSetCategory: (Set<String>, String) -> Unit = { _, _ -> },
    onSaveAppSettings: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onMoveFeatured: (String, Int) -> Unit = { _, _ -> },
    onCreateCollection: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onUpdateCollection: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteCollection: (String) -> Unit = {},
    onSetCollectionWallpapers: (String, List<String>) -> Unit = { _, _ -> },
    onReorderCollection: (String, List<String>) -> Unit = { _, _ -> },
    onAddTag: (String, List<String>) -> Unit = { _, _ -> },
    onRenameTag: (String, String) -> Unit = { _, _ -> },
    onDeleteTag: (String) -> Unit = {},
    onOpenCollection: (WallpaperCollection) -> Unit = {},
) {
    var sectionName by rememberSaveable { mutableStateOf(StudioSection.PUBLISH.name) }
    val section = StudioSection.entries.firstOrNull { it.name == sectionName }
        ?: StudioSection.PUBLISH
    var editWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var deleteWallpaper by remember { mutableStateOf<Wallpaper?>(null) }
    var categoryEditor by remember { mutableStateOf<WallpaperCategory?>(null) }
    var categoryToDelete by remember { mutableStateOf<WallpaperCategory?>(null) }
    var collectionEditor by remember { mutableStateOf<WallpaperCollection?>(null) }
    var collectionToDelete by remember { mutableStateOf<WallpaperCollection?>(null) }
    var collectionWalls by remember { mutableStateOf<WallpaperCollection?>(null) }
    var collectionOrder by remember { mutableStateOf<WallpaperCollection?>(null) }
    var tagRename by remember { mutableStateOf<String?>(null) }
    var tagToDelete by remember { mutableStateOf<String?>(null) }
    var showTagCreate by remember { mutableStateOf(false) }
    var showArchivedWalls by rememberSaveable { mutableStateOf(false) }
    var wallsQuery by rememberSaveable { mutableStateOf("") }
    var wallsCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var wallsFeaturedOnly by rememberSaveable { mutableStateOf(false) }
    var wallsSortName by rememberSaveable { mutableStateOf(StudioWallSort.RECENT.name) }
    val wallsSort = StudioWallSort.entries.firstOrNull { it.name == wallsSortName }
        ?: StudioWallSort.RECENT
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var bulkCategoryDialog by remember { mutableStateOf(false) }
    var bulkDeleteConfirm by remember { mutableStateOf(false) }
    var emptyTrashConfirm by remember { mutableStateOf(false) }
    var wallpaperToPurge by remember { mutableStateOf<Wallpaper?>(null) }
    var replacingWallpaperId by remember { mutableStateOf<String?>(null) }
    val replacePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val id = replacingWallpaperId
        if (uri != null && id != null) onReplaceImage(id, uri)
        replacingWallpaperId = null
    }

    val session = state.session
    LaunchedEffect(session?.userId) {
        if (session != null) onLoadStudio()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        item(key = "studio-header") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_back), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.weight(1f))
                    if (session != null) {
                        Text(
                            text = session.email,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        FilledTonalIconButton(onClick = onLoadStudio) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.studio_refresh),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalIconButton(onClick = onSignOut) {
                            Icon(
                                Icons.Rounded.Logout,
                                contentDescription = stringResource(R.string.settings_sign_out),
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.studio_title),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    text = if (session == null) {
                        stringResource(R.string.studio_subtitle)
                    } else {
                        stringResource(R.string.studio_subtitle_admin)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        when {
            state.isDemo -> item(key = "studio-setup") {
                SetupCard(onRefresh = onRefresh)
            }

            session == null -> item(key = "studio-login") {
                LoginCard(state = state, onSignIn = onSignIn)
            }

            else -> {
                item(key = "studio-overview") {
                    Column {
                        StudioOverview(
                            state = state,
                            onOpenPublish = { sectionName = StudioSection.PUBLISH.name },
                            onOpenArchived = {
                                showArchivedWalls = true
                                wallsQuery = ""
                                wallsCategoryId = null
                                wallsFeaturedOnly = false
                                sectionName = StudioSection.WALLS.name
                            },
                            onOpenFeatured = {
                                showArchivedWalls = false
                                wallsQuery = ""
                                wallsCategoryId = null
                                wallsFeaturedOnly = true
                                sectionName = StudioSection.WALLS.name
                            },
                            onOpenFeaturedOrder = {
                                sectionName = StudioSection.FEATURED.name
                            },
                            onOpenTrash = {
                                sectionName = StudioSection.TRASH.name
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
                item(key = "studio-tabs") {
                    Column {
                        StudioTabs(
                            selected = section,
                            onSelect = { sectionName = it.name },
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (state.studioBusy) {
                    item(key = "studio-busy") {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.studio_saving),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                when (section) {
                    StudioSection.PUBLISH -> item(key = "studio-publish") {
                        PublishPanel(
                            state = state,
                            onUpload = onUpload,
                            onSelectUploadUri = onSelectUploadUri,
                        )
                    }

                    StudioSection.WALLS -> wallsPanel(
                        state = state,
                        showArchived = showArchivedWalls,
                        onToggleArchived = {
                            showArchivedWalls = !showArchivedWalls
                            wallsFeaturedOnly = false
                        },
                        query = wallsQuery,
                        onQueryChange = { wallsQuery = it },
                        categoryId = wallsCategoryId,
                        onCategoryChange = { wallsCategoryId = it },
                        featuredOnly = wallsFeaturedOnly,
                        onFeaturedOnlyChange = { wallsFeaturedOnly = it },
                        sort = wallsSort,
                        onSortChange = { wallsSortName = it.name },
                        onClearFilters = {
                            wallsQuery = ""
                            wallsCategoryId = null
                            wallsFeaturedOnly = false
                            wallsSortName = StudioWallSort.RECENT.name
                        },
                        onPublish = { sectionName = StudioSection.PUBLISH.name },
                        onToggleFeatured = { wallpaper ->
                            onUpdateWallpaper(
                                wallpaper.id,
                                wallpaper.title,
                                wallpaper.category,
                                wallpaper.categoryId,
                                wallpaper.author,
                                wallpaper.tags,
                                !wallpaper.featured,
                            )
                        },
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelectionMode = {
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedIds = emptySet()
                        },
                        onToggleSelected = { id ->
                            selectedIds = if (id in selectedIds) {
                                selectedIds - id
                            } else {
                                selectedIds + id
                            }
                        },
                        onSelectAll = { ids -> selectedIds = ids },
                        onClearSelection = { selectedIds = emptySet() },
                        onBulkFeature = { value ->
                            onBulkSetFeatured(selectedIds, value)
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        onBulkArchive = { value ->
                            onBulkSetArchived(selectedIds, value)
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        onBulkCategory = { bulkCategoryDialog = true },
                        onBulkDelete = { bulkDeleteConfirm = true },
                        onEdit = { editWallpaper = it },
                        onReplace = { wallpaper ->
                            replacingWallpaperId = wallpaper.id
                            replacePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onArchive = onArchiveWallpaper,
                        onDelete = { deleteWallpaper = it },
                    )

                    StudioSection.TRASH -> trashPanel(
                        state = state,
                        onRestore = { id -> onRestoreWallpapers(setOf(id)) },
                        onPurge = { wallpaper -> wallpaperToPurge = wallpaper },
                        onEmptyTrash = { emptyTrashConfirm = true },
                    )

                    StudioSection.CATEGORIES -> item(key = "studio-categories") {
                        CategoriesPanel(
                            state = state,
                            onCreate = onCreateCategory,
                            onEdit = { categoryEditor = it },
                            onMove = onMoveCategory,
                            onDelete = { categoryToDelete = it },
                        )
                    }

                    StudioSection.TAGS -> item(key = "studio-tags") {
                        TagsPanel(
                            state = state,
                            onCreate = { showTagCreate = true },
                            onRename = { tagRename = it },
                            onDelete = { tagToDelete = it },
                        )
                    }

                    StudioSection.COLLECTIONS -> item(key = "studio-collections") {
                        CollectionsPanel(
                            state = state,
                            onCreate = {
                                collectionEditor = WallpaperCollection(id = "", name = "")
                            },
                            onEdit = { collectionEditor = it },
                            onDelete = { collectionToDelete = it },
                            onManageWalls = { collectionWalls = it },
                            onOrder = { collectionOrder = it },
                            onOpen = onOpenCollection,
                        )
                    }

                    StudioSection.CONTENT -> item(key = "studio-content") {
                        ContentPanel(
                            state = state,
                            onSave = onSaveAppSettings,
                        )
                    }

                    StudioSection.FEATURED -> item(key = "studio-featured-order") {
                        FeaturedOrderPanel(
                            state = state,
                            onMove = onMoveFeatured,
                        )
                    }
                }
            }
        }
        item(key = "studio-bottom") {
            Spacer(Modifier.height(32.dp))
        }
    }

    editWallpaper?.let { wallpaper ->
        WallpaperEditDialog(
            wallpaper = wallpaper,
            state = state,
            onDismiss = { editWallpaper = null },
            onSave = { title, category, categoryId, author, tags, featured ->
                onUpdateWallpaper(
                    wallpaper.id,
                    title,
                    category,
                    categoryId,
                    author,
                    tags,
                    featured,
                )
                editWallpaper = null
            },
        )
    }
    deleteWallpaper?.let { wallpaper ->
        ConfirmDialog(
            title = stringResource(R.string.studio_wallpaper_delete_title),
            text = stringResource(R.string.studio_wallpaper_delete_body, wallpaper.title),
            confirm = stringResource(R.string.studio_delete_confirm),
            onDismiss = { deleteWallpaper = null },
            onConfirm = {
                onDeleteWallpaper(wallpaper.id)
                deleteWallpaper = null
            },
        )
    }
    categoryEditor?.let { category ->
        CategoryEditDialog(
            category = category,
            isNew = category.id.isBlank(),
            onDismiss = { categoryEditor = null },
            onSave = { name ->
                if (category.id.isBlank()) {
                    onCreateCategory(name)
                } else {
                    onUpdateCategory(category.id, name)
                }
                categoryEditor = null
            },
        )
    }
    categoryToDelete?.let { category ->
        CategoryDeleteDialog(
            category = category,
            categories = state.categories,
            count = state.studioWallpapers.count { it.categoryId == category.id },
            onDismiss = { categoryToDelete = null },
            onConfirm = { reassignTo ->
                onDeleteCategory(category.id, reassignTo)
                categoryToDelete = null
            },
        )
    }
    collectionEditor?.let { collection ->
        CollectionEditDialog(
            collection = collection,
            isNew = collection.id.isBlank(),
            onDismiss = { collectionEditor = null },
            onSave = { name, description, isPublic ->
                if (collection.id.isBlank()) {
                    onCreateCollection(name, description, isPublic)
                } else {
                    onUpdateCollection(collection.id, name, description, isPublic)
                }
                collectionEditor = null
            },
        )
    }
    collectionToDelete?.let { collection ->
        ConfirmDialog(
            title = stringResource(R.string.studio_collection_delete_title),
            text = stringResource(R.string.studio_collection_delete_body, collection.name),
            confirm = stringResource(R.string.studio_delete_confirm),
            onDismiss = { collectionToDelete = null },
            onConfirm = {
                onDeleteCollection(collection.id)
                collectionToDelete = null
            },
        )
    }
    collectionWalls?.let { collection ->
        CollectionWallsDialog(
            collection = collection,
            wallpapers = state.studioWallpapers,
            onDismiss = { collectionWalls = null },
            onSave = { selectedIds ->
                onSetCollectionWallpapers(collection.id, selectedIds.toList())
                collectionWalls = null
            },
        )
    }
    collectionOrder?.let { collection ->
        CollectionOrderDialog(
            collection = collection,
            wallpapers = state.studioWallpapers,
            onDismiss = { collectionOrder = null },
            onSave = { ids ->
                onReorderCollection(collection.id, ids)
                collectionOrder = null
            },
        )
    }
    if (bulkCategoryDialog) {
        BulkCategoryDialog(
            categories = state.categories,
            onDismiss = { bulkCategoryDialog = false },
            onSelect = { categoryId ->
                onBulkSetCategory(selectedIds, categoryId)
                bulkCategoryDialog = false
                selectionMode = false
                selectedIds = emptySet()
            },
        )
    }
    if (bulkDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.studio_bulk_delete_title),
            text = stringResource(
                R.string.studio_bulk_delete_body,
                selectedIds.size,
            ),
            confirm = stringResource(R.string.studio_delete_confirm),
            onDismiss = { bulkDeleteConfirm = false },
            onConfirm = {
                onBulkDeleteWallpapers(selectedIds)
                bulkDeleteConfirm = false
                selectionMode = false
                selectedIds = emptySet()
            },
        )
    }
    wallpaperToPurge?.let { wallpaper ->
        ConfirmDialog(
            title = stringResource(R.string.studio_trash_purge_title),
            text = stringResource(R.string.studio_trash_purge_body, wallpaper.title),
            confirm = stringResource(R.string.studio_trash_purge_confirm),
            onDismiss = { wallpaperToPurge = null },
            onConfirm = {
                onPurgeWallpaper(wallpaper.id)
                wallpaperToPurge = null
            },
        )
    }
    if (emptyTrashConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.studio_trash_empty_title),
            text = stringResource(R.string.studio_trash_empty_body),
            confirm = stringResource(R.string.studio_trash_empty_confirm),
            onDismiss = { emptyTrashConfirm = false },
            onConfirm = {
                onEmptyTrash()
                emptyTrashConfirm = false
            },
        )
    }
    tagRename?.let { tag ->
        TagRenameDialog(
            tag = tag,
            onDismiss = { tagRename = null },
            onSave = { next ->
                onRenameTag(tag, next)
                tagRename = null
            },
        )
    }
    tagToDelete?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.studio_tag_delete_title),
            text = stringResource(R.string.studio_tag_delete_body, tag),
            confirm = stringResource(R.string.studio_delete_confirm),
            onDismiss = { tagToDelete = null },
            onConfirm = {
                onDeleteTag(tag)
                tagToDelete = null
            },
        )
    }
    if (showTagCreate) {
        TagCreateDialog(
            wallpapers = state.studioWallpapers,
            onDismiss = { showTagCreate = false },
            onSave = { tag, ids ->
                onAddTag(tag, ids)
                showTagCreate = false
            },
        )
    }
}

@Composable
private fun StudioOverview(
    state: UiState,
    onOpenPublish: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenFeatured: () -> Unit,
    onOpenFeaturedOrder: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val archived = state.studioWallpapers.count { it.isArchived }
    val published = state.studioWallpapers.size - archived
    val featured = state.studioWallpapers.count { it.featured }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.studio_overview),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Image,
                    label = stringResource(R.string.studio_published),
                    value = published,
                )
                StudioStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Archive,
                    label = stringResource(R.string.studio_archived),
                    value = archived,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudioStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Star,
                    label = stringResource(R.string.studio_featured),
                    value = featured,
                )
                StudioStat(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Folder,
                    label = stringResource(R.string.studio_collections),
                    value = state.myCollections.size,
                )
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item(key = "overview-publish") {
                    TextButton(onClick = onOpenPublish) {
                        Text(stringResource(R.string.studio_new_wallpaper))
                    }
                }
                item(key = "overview-archived") {
                    TextButton(onClick = onOpenArchived) {
                        Text(stringResource(R.string.studio_archived))
                    }
                }
                item(key = "overview-featured") {
                    TextButton(onClick = onOpenFeatured) {
                        Text(stringResource(R.string.studio_featured))
                    }
                }
                item(key = "overview-featured-order") {
                    TextButton(onClick = onOpenFeaturedOrder) {
                        Text(stringResource(R.string.studio_order_featured))
                    }
                }
                item(key = "overview-trash") {
                    TextButton(onClick = onOpenTrash) {
                        Text(
                            stringResource(
                                R.string.studio_trash_count,
                                state.trashWallpapers.size,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: Int,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ContentPanel(
    state: UiState,
    onSave: (String, String, Boolean) -> Unit,
) {
    var tagline by rememberSaveable(state.appSettings.tagline) {
        mutableStateOf(state.appSettings.tagline)
    }
    var bannerText by rememberSaveable(state.appSettings.bannerText) {
        mutableStateOf(state.appSettings.bannerText)
    }
    var bannerEnabled by rememberSaveable(state.appSettings.bannerEnabled) {
        mutableStateOf(state.appSettings.bannerEnabled)
    }

    Text(
        stringResource(R.string.studio_content),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.studio_content_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(
        value = tagline,
        onValueChange = { tagline = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.studio_tagline)) },
        supportingText = { Text(stringResource(R.string.studio_tagline_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = bannerText,
        onValueChange = { bannerText = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.studio_banner_text)) },
        supportingText = { Text(stringResource(R.string.studio_banner_hint)) },
        minLines = 3,
        maxLines = 5,
        shape = MaterialTheme.shapes.medium,
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.studio_banner_enabled),
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = bannerEnabled,
            onCheckedChange = { bannerEnabled = it },
        )
    }
    Spacer(Modifier.height(18.dp))
    ContentPreview(
        state = state,
        tagline = tagline,
        bannerText = bannerText,
        bannerEnabled = bannerEnabled,
    )
    Spacer(Modifier.height(14.dp))
    Button(
        onClick = { onSave(tagline, bannerText, bannerEnabled) },
        enabled = !state.studioBusy,
        shape = CircleShape,
    ) {
        Text(stringResource(R.string.studio_save_content))
    }
}

@Composable
private fun ContentPreview(
    state: UiState,
    tagline: String,
    bannerText: String,
    bannerEnabled: Boolean,
) {
    val previewTagline = tagline.ifBlank { stringResource(R.string.home_tagline) }
    val featured = state.featured.take(3)

    Text(
        text = stringResource(R.string.studio_content_preview),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.studio_content_preview_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = previewTagline,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (bannerEnabled && bannerText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            if (featured.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    featured.forEach { wallpaper ->
                        AsyncImage(
                            model = wallpaper.thumbnailUrl,
                            contentDescription = wallpaper.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .height(110.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedOrderPanel(
    state: UiState,
    onMove: (String, Int) -> Unit,
) {
    val featured = state.studioWallpapers
        .filter { it.featured && !it.isArchived }
        .sortedBy { it.featuredPosition }

    Text(
        stringResource(R.string.studio_featured_order),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.studio_featured_order_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (featured.isEmpty()) {
        Text(
            text = stringResource(R.string.studio_featured_order_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    featured.forEachIndexed { index, wallpaper ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = wallpaper.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.categoryNameFor(wallpaper),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onMove(wallpaper.id, -1) },
                    enabled = index > 0 && !state.studioBusy,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.studio_move_up),
                    )
                }
                IconButton(
                    onClick = { onMove(wallpaper.id, 1) },
                    enabled = index < featured.lastIndex && !state.studioBusy,
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.studio_move_down),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioTabs(
    selected: StudioSection,
    onSelect: (StudioSection) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 12.dp),
    ) {
        items(StudioSection.entries, key = { it.name }) { section ->
            FilterChip(
                selected = selected == section,
                onClick = { onSelect(section) },
                label = { Text(stringResource(section.labelRes)) },
                leadingIcon = {
                    Icon(
                        imageVector = section.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun SetupCard(onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Icon(
                imageVector = Icons.Rounded.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.studio_setup_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.studio_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRefresh, shape = CircleShape) {
                Text(stringResource(R.string.studio_retry_connection))
            }
        }
    }
}

@Composable
private fun LoginCard(
    state: UiState,
    onSignIn: (String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.studio_admin_only),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.studio_login_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.studio_email)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Person, contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.studio_password)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Lock, contentDescription = null)
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSignIn(email, password) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                enabled = !state.authBusy,
            ) {
                if (state.authBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.studio_signing_in))
                } else {
                    Text(stringResource(R.string.studio_sign_in))
                }
            }
        }
    }
}

@Composable
private fun PublishPanel(
    state: UiState,
    onUpload: (Uri, String, String, String?, List<String>, String?) -> Unit,
    onSelectUploadUri: (Uri?) -> Unit,
) {
    val selectedUri = state.selectedUploadUri?.let(Uri::parse)
    val categories = state.categoryOptions.ifEmpty {
        DefaultCategories.mapIndexed { index, name ->
            WallpaperCategory(id = "", name = name, position = index)
        }
    }
    val myCollections = state.myCollections
    var title by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id.orEmpty()) }
    var tagsText by rememberSaveable { mutableStateOf("") }
    var selectedCollectionId by rememberSaveable { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current
    val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId }
        ?: categories.firstOrNull()
    val defaultCategory = stringResource(R.string.field_default_category)

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onSelectUploadUri(uri)
    }

    LaunchedEffect(state.uploadSuccessCount) {
        if (state.uploadSuccessCount > 0) {
            onSelectUploadUri(null)
            title = ""
            tagsText = ""
        }
    }

    Text(
        text = stringResource(R.string.studio_new_wallpaper),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(12.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.large,
                    )
                    .clickable {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedUri == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.studio_choose_image),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.studio_image_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = stringResource(R.string.studio_preview),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    TextButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.studio_change_image))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_title)) },
                placeholder = { Text(stringResource(R.string.studio_title_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                items(categories, key = { it.id.ifBlank { it.name } }) { option ->
                    FilterChip(
                        selected = selectedCategory?.name == option.name,
                        onClick = { selectedCategoryId = option.id },
                        label = { Text(option.name) },
                        shape = CircleShape,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.studio_section_tags)) },
                placeholder = { Text(stringResource(R.string.studio_tags_hint)) },
                supportingText = {
                    Text(stringResource(R.string.studio_tags_help))
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            TagPreviewChips(parseTags(tagsText))
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.studio_collection_optional),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            if (myCollections.isEmpty()) {
                Text(
                    text = stringResource(R.string.studio_no_collections_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 12.dp),
                ) {
                    item(key = "no-collection") {
                        FilterChip(
                            selected = selectedCollectionId.isBlank(),
                            onClick = { selectedCollectionId = "" },
                            label = { Text(stringResource(R.string.studio_no_collection)) },
                            shape = CircleShape,
                        )
                    }
                    items(myCollections, key = { it.id }) { collection ->
                        FilterChip(
                            selected = selectedCollectionId == collection.id,
                            onClick = { selectedCollectionId = collection.id },
                            label = { Text(collection.name) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    selectedUri?.let { uri ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUpload(
                            uri,
                            title,
                            selectedCategory?.name ?: defaultCategory,
                            selectedCategory?.id?.ifBlank { null },
                            parseTags(tagsText),
                            selectedCollectionId.ifBlank { null },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                enabled = selectedUri != null && !state.uploading,
            ) {
                AnimatedContent(
                    targetState = state.uploading,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f))
                            .togetherWith(
                                fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.95f),
                            )
                    },
                    label = "uploadAction",
                ) { uploading ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.studio_uploading))
                        } else {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.studio_publish))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.wallsPanel(
    state: UiState,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    categoryId: String?,
    onCategoryChange: (String?) -> Unit,
    featuredOnly: Boolean,
    onFeaturedOnlyChange: (Boolean) -> Unit,
    sort: StudioWallSort,
    onSortChange: (StudioWallSort) -> Unit,
    onClearFilters: () -> Unit,
    onPublish: () -> Unit,
    onToggleFeatured: (Wallpaper) -> Unit,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelectionMode: () -> Unit,
    onToggleSelected: (String) -> Unit,
    onSelectAll: (Set<String>) -> Unit,
    onClearSelection: () -> Unit,
    onBulkFeature: (Boolean) -> Unit,
    onBulkArchive: (Boolean) -> Unit,
    onBulkCategory: () -> Unit,
    onBulkDelete: () -> Unit,
    onEdit: (Wallpaper) -> Unit,
    onReplace: (Wallpaper) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (Wallpaper) -> Unit,
) {
    val term = query.trim()
    val hasFilters = query.isNotBlank() || categoryId != null || featuredOnly
    val archivedCount = state.studioWallpapers.count { it.isArchived }
    val walls = state.studioWallpapers
        .filter { it.isArchived == showArchived }
        .filter { categoryId == null || it.categoryId == categoryId }
        .filter { !featuredOnly || it.featured }
        .filter { wallpaper ->
            term.isBlank() ||
                wallpaper.title.contains(term, ignoreCase = true) ||
                wallpaper.tags.any { tag -> tag.contains(term, ignoreCase = true) }
        }
        .let { filtered ->
            when (sort) {
                StudioWallSort.RECENT -> filtered.sortedByDescending {
                    it.createdAt.ifBlank { it.id }
                }
                StudioWallSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
                StudioWallSort.CATEGORY -> filtered.sortedWith(
                    compareBy<Wallpaper> { state.categoryNameFor(it).lowercase() }
                        .thenBy { it.title.lowercase() },
                )
            }
        }

    item(key = "walls-header") {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.studio_my_walls),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = showArchived,
                    onClick = onToggleArchived,
                    label = {
                        Text(
                            if (showArchived) {
                                stringResource(R.string.studio_archived)
                            } else {
                                stringResource(R.string.studio_published)
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showArchived) {
                                Icons.Rounded.Archive
                            } else {
                                Icons.Rounded.Image
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    shape = CircleShape,
                )
                IconButton(onClick = onToggleSelectionMode) {
                    Icon(
                        imageVector = if (selectionMode) {
                            Icons.Rounded.Close
                        } else {
                            Icons.Rounded.Check
                        },
                        contentDescription = stringResource(R.string.studio_select),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    hasFilters -> pluralStringResource(
                        R.plurals.wallpapers_count,
                        walls.size,
                        walls.size,
                    )
                    showArchived -> pluralStringResource(
                        R.plurals.studio_archived_count,
                        archivedCount,
                        archivedCount,
                    )
                    else -> {
                        val publishedCount = state.studioWallpapers.size - archivedCount
                        pluralStringResource(
                            R.plurals.studio_published_count,
                            publishedCount,
                            publishedCount,
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (selectionMode) {
        item(key = "walls-bulk") {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.studio_selected_count,
                                    selectedIds.size,
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearSelection) {
                                Text(stringResource(R.string.studio_clear_selection))
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item(key = "bulk-select-all") {
                                TextButton(
                                    onClick = { onSelectAll(walls.map { it.id }.toSet()) },
                                    enabled = walls.isNotEmpty() && !state.studioBusy,
                                ) {
                                    Text(stringResource(R.string.studio_select_all))
                                }
                            }
                            item(key = "bulk-feature") {
                                TextButton(
                                    onClick = { onBulkFeature(true) },
                                    enabled = selectedIds.isNotEmpty() && !state.studioBusy,
                                ) {
                                    Text(stringResource(R.string.studio_feature))
                                }
                            }
                            item(key = "bulk-unfeature") {
                                TextButton(
                                    onClick = { onBulkFeature(false) },
                                    enabled = selectedIds.isNotEmpty() && !state.studioBusy,
                                ) {
                                    Text(stringResource(R.string.studio_unfeature))
                                }
                            }
                            item(key = "bulk-archive") {
                                TextButton(
                                    onClick = { onBulkArchive(true) },
                                    enabled = selectedIds.isNotEmpty() && !state.studioBusy,
                                ) {
                                    Text(stringResource(R.string.studio_archive))
                                }
                            }
                            item(key = "bulk-restore") {
                                TextButton(
                                    onClick = { onBulkArchive(false) },
                                    enabled = selectedIds.isNotEmpty() && !state.studioBusy,
                                ) {
                                    Text(stringResource(R.string.studio_restore))
                                }
                            }
                            if (state.categories.isNotEmpty()) {
                                item(key = "bulk-category") {
                                    TextButton(
                                        onClick = onBulkCategory,
                                        enabled = selectedIds.isNotEmpty() && !state.studioBusy,
                                    ) {
                                        Text(stringResource(R.string.studio_bulk_category))
                                    }
                                }
                            }
                            item(key = "bulk-delete") {
                                TextButton(
                                    onClick = onBulkDelete,
                                    enabled = selectedIds.isNotEmpty() && !state.studioBusy,
                                ) {
                                    Text(
                                        text = stringResource(R.string.action_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    item(key = "walls-filters") {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.studio_search_walls)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.home_clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                item(key = "walls-featured-filter") {
                    FilterChip(
                        selected = featuredOnly,
                        onClick = { onFeaturedOnlyChange(!featuredOnly) },
                        label = { Text(stringResource(R.string.studio_featured)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        shape = CircleShape,
                    )
                }
                if (state.categories.isNotEmpty()) {
                    item(key = "walls-all-categories") {
                        FilterChip(
                            selected = categoryId == null,
                            onClick = { onCategoryChange(null) },
                            label = { Text(stringResource(R.string.studio_all)) },
                            shape = CircleShape,
                        )
                    }
                    items(
                        items = state.categories,
                        key = { "walls-category-${it.id}" },
                    ) { category ->
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = {
                                onCategoryChange(
                                    if (categoryId == category.id) null else category.id,
                                )
                            },
                            label = { Text(category.name) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 12.dp),
            ) {
                item(key = "walls-sort-label") {
                    Text(
                        text = stringResource(R.string.studio_sort),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(
                    items = StudioWallSort.entries,
                    key = { "walls-sort-${it.name}" },
                ) { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { onSortChange(option) },
                        label = { Text(stringResource(option.labelRes)) },
                        shape = CircleShape,
                    )
                }
            }
            if (hasFilters) {
                TextButton(onClick = onClearFilters) {
                    Text(stringResource(R.string.state_clear_filters))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (walls.isEmpty()) {
        item(key = "walls-empty") {
            Column {
                Text(
                    text = when {
                        hasFilters -> stringResource(R.string.studio_no_results)
                        showArchived -> stringResource(R.string.studio_walls_empty_archived)
                        else -> stringResource(R.string.studio_walls_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                when {
                    hasFilters -> TextButton(onClick = onClearFilters) {
                        Text(stringResource(R.string.state_clear_filters))
                    }
                    showArchived -> TextButton(onClick = onToggleArchived) {
                        Text(stringResource(R.string.studio_published))
                    }
                    else -> Button(onClick = onPublish, shape = CircleShape) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.studio_new_wallpaper))
                    }
                }
            }
        }
    } else {
        items(items = walls, key = { it.id }) { wallpaper ->
            StudioWallpaperRow(
                wallpaper = wallpaper,
                categoryName = state.categoryNameFor(wallpaper),
                selectable = selectionMode,
                selected = wallpaper.id in selectedIds,
                onToggleSelected = { onToggleSelected(wallpaper.id) },
                onEdit = { onEdit(wallpaper) },
                onReplace = { onReplace(wallpaper) },
                onToggleFeatured = { onToggleFeatured(wallpaper) },
                onArchive = { onArchive(wallpaper.id, !wallpaper.isArchived) },
                onDelete = { onDelete(wallpaper) },
            )
        }
    }
}

private fun LazyListScope.trashPanel(
    state: UiState,
    onRestore: (String) -> Unit,
    onPurge: (Wallpaper) -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val trash = state.trashWallpapers
    item(key = "trash-header") {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.studio_trash),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (trash.isNotEmpty()) {
                    TextButton(
                        onClick = onEmptyTrash,
                        enabled = !state.studioBusy,
                    ) {
                        Text(
                            text = stringResource(R.string.studio_trash_empty_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.studio_trash_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.studio_trash_count, trash.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (trash.isEmpty()) {
        item(key = "trash-empty") {
            Text(
                text = stringResource(R.string.studio_trash_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    items(items = trash, key = { "trash-${it.id}" }) { wallpaper ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = wallpaper.thumbnailUrl,
                    contentDescription = wallpaper.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 52.dp, height = 72.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = wallpaper.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.categoryNameFor(wallpaper),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onRestore(wallpaper.id) },
                    enabled = !state.studioBusy,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Unarchive,
                        contentDescription = stringResource(R.string.studio_restore),
                    )
                }
                IconButton(
                    onClick = { onPurge(wallpaper) },
                    enabled = !state.studioBusy,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.studio_trash_purge_confirm),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioWallpaperRow(
    wallpaper: Wallpaper,
    categoryName: String,
    selectable: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onToggleFeatured: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clickable(enabled = selectable, onClick = onToggleSelected),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectable) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelected() },
                )
            }
            AsyncImage(
                model = wallpaper.thumbnailUrl,
                contentDescription = wallpaper.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 66.dp, height = 88.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = wallpaper.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (wallpaper.tags.isNotEmpty()) {
                    Text(
                        text = wallpaper.tags.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (wallpaper.isArchived) {
                    Text(
                        text = stringResource(R.string.studio_archived_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            if (!selectable) {
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.studio_edit),
                            )
                        }
                        IconButton(onClick = onReplace) {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.studio_image),
                            )
                        }
                        IconButton(onClick = onToggleFeatured) {
                            Icon(
                                imageVector = if (wallpaper.featured) {
                                    Icons.Rounded.Star
                                } else {
                                    Icons.Rounded.StarBorder
                                },
                                contentDescription = stringResource(
                                    if (wallpaper.featured) {
                                        R.string.studio_unfeature
                                    } else {
                                        R.string.studio_feature
                                    },
                                ),
                                tint = if (wallpaper.featured) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onArchive) {
                            Icon(
                                imageVector = if (wallpaper.isArchived) {
                                    Icons.Rounded.Unarchive
                                } else {
                                    Icons.Rounded.Archive
                                },
                                contentDescription = if (wallpaper.isArchived) {
                                    stringResource(R.string.studio_restore)
                                } else {
                                    stringResource(R.string.studio_archive)
                                },
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesPanel(
    state: UiState,
    onCreate: (String) -> Unit,
    onEdit: (WallpaperCategory) -> Unit,
    onMove: (String, Int) -> Unit,
    onDelete: (WallpaperCategory) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val counts = remember(state.studioWallpapers) {
        buildMap<String, Int> {
            state.studioWallpapers.forEach { wallpaper ->
                val id = wallpaper.categoryId ?: return@forEach
                this[id] = (this[id] ?: 0) + 1
            }
        }
    }
    Text(
        stringResource(R.string.studio_categories),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.studio_categories_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.studio_new_category)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = {
                onCreate(name)
                name = ""
            },
            enabled = name.isNotBlank() && !state.studioBusy,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = stringResource(R.string.studio_create_category),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (state.categories.isEmpty()) {
        Text(
            text = stringResource(R.string.studio_categories_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    state.categories.forEachIndexed { index, category ->
        val count = counts[category.id] ?: 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(R.plurals.wallpapers_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onMove(category.id, -1) },
                enabled = index > 0 && !state.studioBusy,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.studio_move_up),
                )
            }
            IconButton(
                onClick = { onMove(category.id, 1) },
                enabled = index < state.categories.lastIndex && !state.studioBusy,
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.studio_move_down),
                )
            }
            IconButton(onClick = { onEdit(category) }) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.studio_rename),
                )
            }
            IconButton(onClick = { onDelete(category) }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider()
    }
}

/**
 * Vista previa de los tags cargados en un formulario. El primero queda
 * destacado porque es el principal: es el que aparece en la fila de Inicio.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPreviewChips(tags: List<String>) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEachIndexed { index, tag ->
            val primary = index == 0
            Surface(
                shape = CircleShape,
                color = if (primary) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (primary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (primary) FontWeight.SemiBold else null,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TagsPanel(
    state: UiState,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val counts = remember(state.studioWallpapers) {
        val result = linkedMapOf<String, Pair<String, Int>>()
        state.studioWallpapers.forEach { wallpaper ->
            wallpaper.tags.forEach { tag ->
                val key = tag.lowercase()
                val previous = result[key]
                result[key] = if (previous == null) {
                    tag to 1
                } else {
                    previous.first to previous.second + 1
                }
            }
        }
        result.values.toList()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.studio_section_tags),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onCreate, shape = CircleShape) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.studio_create_tag))
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.studio_tags_help_section),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (counts.isEmpty()) {
        Text(
            text = stringResource(R.string.studio_tags_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    counts.sortedBy { it.first.lowercase() }.forEach { (tag, count) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.LocalOffer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tag, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(R.plurals.wallpapers_count, count, count),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRename(tag) }) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.studio_rename),
                )
            }
            IconButton(onClick = { onDelete(tag) }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun CollectionsPanel(
    state: UiState,
    onCreate: () -> Unit,
    onEdit: (WallpaperCollection) -> Unit,
    onDelete: (WallpaperCollection) -> Unit,
    onManageWalls: (WallpaperCollection) -> Unit,
    onOrder: (WallpaperCollection) -> Unit,
    onOpen: (WallpaperCollection) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.studio_collections),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onCreate, shape = CircleShape) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_create))
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.studio_collections_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (state.myCollections.isEmpty()) {
        Text(
            text = stringResource(R.string.studio_collections_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    state.myCollections.forEach { collection ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(collection.name, style = MaterialTheme.typography.titleMedium)
                        val visibility = if (collection.isPublic) {
                            stringResource(R.string.collection_public)
                        } else {
                            stringResource(R.string.collection_private_badge)
                        }
                        Text(
                            text = pluralStringResource(
                                R.plurals.collection_meta,
                                collection.wallpaperIds.size,
                                collection.wallpaperIds.size,
                                visibility,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(collection) }) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.studio_edit),
                        )
                    }
                    IconButton(onClick = { onDelete(collection) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (collection.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = collection.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onManageWalls(collection) }) {
                        Text(stringResource(R.string.studio_walls))
                    }
                    OutlinedButton(onClick = { onOrder(collection) }) {
                        Text(stringResource(R.string.studio_order))
                    }
                    OutlinedButton(onClick = { onOpen(collection) }) {
                        Text(stringResource(R.string.studio_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkCategoryDialog(
    categories: List<WallpaperCategory>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_bulk_category_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(category.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun WallpaperEditDialog(
    wallpaper: Wallpaper,
    state: UiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, String, List<String>, Boolean) -> Unit,
) {
    var title by remember(wallpaper.id) { mutableStateOf(wallpaper.title) }
    var author by remember(wallpaper.id) { mutableStateOf(wallpaper.author) }
    var tags by remember(wallpaper.id) { mutableStateOf(wallpaper.tags.joinToString(", ")) }
    var featured by remember(wallpaper.id) { mutableStateOf(wallpaper.featured) }
    var categoryId by remember(wallpaper.id) { mutableStateOf(wallpaper.categoryId) }
    val noCategory = stringResource(R.string.studio_no_category)
    val options = remember(wallpaper, state.categories, noCategory) {
        val base = state.categories
        if (wallpaper.categoryId != null && base.none { it.id == wallpaper.categoryId }) {
            base + WallpaperCategory(
                id = wallpaper.categoryId,
                name = wallpaper.category.ifBlank { noCategory },
            )
        } else {
            base
        }
    }
    val selected = options.firstOrNull { it.id == categoryId }
        ?: options.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_edit_wallpaper)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text(stringResource(R.string.field_author)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryId = option.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected?.id == option.id,
                            onCheckedChange = { categoryId = option.id },
                        )
                        Text(option.name)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text(stringResource(R.string.field_tags)) },
                    supportingText = { Text(stringResource(R.string.studio_tags_comma)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                TagPreviewChips(parseTags(tags))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.studio_featured),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = featured, onCheckedChange = { featured = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        selected?.name ?: wallpaper.category,
                        selected?.id,
                        author,
                        parseTags(tags),
                        featured,
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CategoryEditDialog(
    category: WallpaperCategory,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) {
                    stringResource(R.string.studio_new_category)
                } else {
                    stringResource(R.string.studio_rename_category)
                },
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CategoryDeleteDialog(
    category: WallpaperCategory,
    categories: List<WallpaperCategory>,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    val alternatives = categories.filterNot { it.id == category.id }
    var reassignTo by remember(category.id) { mutableStateOf(alternatives.firstOrNull()?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_delete_category)) },
        text = {
            Column {
                Text(
                    if (count == 0) {
                        stringResource(R.string.studio_delete_category_body, category.name)
                    } else {
                        stringResource(
                            R.string.studio_delete_category_has_walls,
                            category.name,
                            count,
                        )
                    },
                )
                if (count > 0) {
                    Spacer(Modifier.height(10.dp))
                    alternatives.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reassignTo = option.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = reassignTo == option.id,
                                onCheckedChange = { reassignTo = option.id },
                            )
                            Text(option.name)
                        }
                    }
                    if (alternatives.isEmpty()) {
                        Text(
                            text = stringResource(R.string.studio_delete_category_create_another),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (count > 0) reassignTo else null) },
                enabled = count == 0 || reassignTo != null,
            ) {
                Text(stringResource(R.string.studio_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CollectionEditDialog(
    collection: WallpaperCollection,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var name by remember(collection.id) { mutableStateOf(collection.name) }
    var description by remember(collection.id) { mutableStateOf(collection.description) }
    var isPublic by remember(collection.id) { mutableStateOf(collection.isPublic) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) {
                    stringResource(R.string.collection_new)
                } else {
                    stringResource(R.string.studio_edit_collection)
                },
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.field_description)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isPublic) {
                                stringResource(R.string.collection_public)
                            } else {
                                stringResource(R.string.collection_private_badge)
                            },
                        )
                        Text(
                            text = if (isPublic) {
                                stringResource(R.string.collection_public_hint)
                            } else {
                                stringResource(R.string.collection_private_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, isPublic) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CollectionWallsDialog(
    collection: WallpaperCollection,
    wallpapers: List<Wallpaper>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val selected = remember(collection.id, collection.wallpaperIds) {
        mutableStateOf(collection.wallpaperIds.toSet())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(collection.name) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "collection-walls-actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            selected.value = wallpapers.map { it.id }.toSet()
                        }) {
                            Text(stringResource(R.string.studio_all))
                        }
                        TextButton(onClick = { selected.value = emptySet() }) {
                            Text(stringResource(R.string.studio_none))
                        }
                    }
                }
                items(items = wallpapers, key = { it.id }) { wallpaper ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.value = if (wallpaper.id in selected.value) {
                                    selected.value - wallpaper.id
                                } else {
                                    selected.value + wallpaper.id
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = wallpaper.id in selected.value,
                            onCheckedChange = { checked ->
                                selected.value = if (checked) {
                                    selected.value + wallpaper.id
                                } else {
                                    selected.value - wallpaper.id
                                }
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                wallpaper.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (wallpaper.isArchived) {
                                Text(
                                    stringResource(R.string.studio_archived_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.value) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun CollectionOrderDialog(
    collection: WallpaperCollection,
    wallpapers: List<Wallpaper>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val byId = wallpapers.associateBy { it.id }
    val initial = remember(collection.id, collection.wallpaperIds) {
        collection.wallpaperIds.filter { it in byId }
    }
    var order by remember(collection.id) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_collection_order_title, collection.name)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(items = order, key = { _, id -> id }) { index, id ->
                    val wallpaper = byId[id]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = wallpaper?.title.orEmpty(),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    order = order.toMutableList().also {
                                        val item = it.removeAt(index)
                                        it.add(index - 1, item)
                                    }
                                }
                            },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.studio_move_up),
                            )
                        }
                        IconButton(
                            onClick = {
                                if (index < order.lastIndex) {
                                    order = order.toMutableList().also {
                                        val item = it.removeAt(index)
                                        it.add(index + 1, item)
                                    }
                                }
                            },
                            enabled = index < order.lastIndex,
                        ) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.studio_move_down),
                            )
                        }
                    }
                }
                if (order.isEmpty()) {
                    item(key = "collection-order-empty") {
                        Text(
                            text = stringResource(R.string.studio_collection_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(order) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TagCreateDialog(
    wallpapers: List<Wallpaper>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    var tag by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_create_tag)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 430.dp)) {
                item(key = "tag-create-header") {
                    Column {
                        OutlinedTextField(
                            value = tag,
                            onValueChange = { tag = it },
                            label = { Text(stringResource(R.string.studio_tag_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.studio_apply_to),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                items(items = wallpapers, key = { it.id }) { wallpaper ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.value = if (wallpaper.id in selected.value) {
                                    selected.value - wallpaper.id
                                } else {
                                    selected.value + wallpaper.id
                                }
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = wallpaper.id in selected.value,
                            onCheckedChange = { checked ->
                                selected.value = if (checked) {
                                    selected.value + wallpaper.id
                                } else {
                                    selected.value - wallpaper.id
                                }
                            },
                        )
                        Text(
                            wallpaper.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(tag, selected.value.toList()) },
                enabled = tag.isNotBlank() && selected.value.isNotEmpty(),
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun TagRenameDialog(
    tag: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var next by remember(tag) { mutableStateOf(tag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_rename_tag)) },
        text = {
            OutlinedTextField(
                value = next,
                onValueChange = { next = it },
                label = { Text(stringResource(R.string.studio_new_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(next) },
                enabled = next.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

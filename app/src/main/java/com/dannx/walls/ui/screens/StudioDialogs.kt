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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.BulkTagResult
import com.dannx.walls.data.BulkTagLimit
import com.dannx.walls.data.DefaultCategories
import com.dannx.walls.data.TagCategory
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.allTags
import com.dannx.walls.data.parseTags

@Composable
internal fun BulkCategoryDialog(
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

/**
 * Edición de tags en bloque: elige sumar un tag, quitar uno o cambiar a todos
 * a otra categoría (ese reutiliza el diálogo ya existente). Antes de tocar
 * nada muestra el resumen con cuántos fondos entran y avisa si quitar un tag
 * le cambia el principal a alguno.
 */
@Composable
internal fun BulkTagsDialog(
    state: UiState,
    selectedIds: List<String>,
    onDismiss: () -> Unit,
    onTags: (ids: List<String>, tag: String, add: Boolean) -> Unit,
    onCategory: () -> Unit,
) {
    var mode by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf("") }

    if (mode == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.studio_bulk_edit_title)) },
            text = {
                Column {
                    if (selectedIds.size > BulkTagLimit) {
                        Text(
                            text = stringResource(
                                R.string.studio_bulk_tag_limit,
                                selectedIds.size,
                                BulkTagLimit,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = { mode = "add" },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.studio_bulk_add_tag))
                    }
                    TextButton(
                        onClick = { mode = "remove" },
                        enabled = selectedIds.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.studio_bulk_remove_tag))
                    }
                    if (state.categories.isNotEmpty()) {
                        TextButton(onClick = onCategory) {
                            Text(stringResource(R.string.studio_bulk_category))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
        return
    }

    val add = mode == "add"
    val cleanTag = tag.trim().trimStart('#').trim()
    val duplicated = state.studioWallpapers
        .filter { it.id in selectedIds }
        .count { wall ->
            if (add) {
                wall.tags.any { it.equals(cleanTag, ignoreCase = true) }
            } else {
                wall.tags.firstOrNull()?.equals(cleanTag, ignoreCase = true) == true
            }
        }
    AlertDialog(
        onDismissRequest = { mode = null },
        title = {
            Text(
                stringResource(
                    if (add) R.string.studio_bulk_add_tag else R.string.studio_bulk_remove_tag,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it.take(24) },
                    label = { Text(stringResource(R.string.field_tags)) },
                    singleLine = true,
                    isError = tag.trim().isBlank() || duplicated == selectedIds.size,
                )
                Spacer(Modifier.height(10.dp))
                val summary = stringResource(
                    if (add) R.string.studio_bulk_tag_summary_add
                    else R.string.studio_bulk_tag_summary_remove,
                    tag.trim(),
                    selectedIds.size,
                )
                Text(summary)
                if (!add && duplicated > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.studio_bulk_first_warn),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTags(selectedIds, cleanTag, add) },
                enabled = cleanTag.isNotBlank() && duplicated < selectedIds.size,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { mode = null }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Resumen final de la corrida: cuántos fondos quedaron bien y cuántos no. */
@Composable
internal fun BulkTagsResultDialog(
    result: BulkTagResult,
    onDismiss: () -> Unit,
    onRetry: (List<String>) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_bulk_edit_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.studio_bulk_result,
                        result.saved,
                        result.saved + result.failed.size,
                    ),
                )
                if (result.failed.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.studio_bulk_failed,
                            result.failed.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (result.failed.isNotEmpty()) {
                TextButton(onClick = { onRetry(result.failed) }) {
                    Text(stringResource(R.string.studio_bulk_retry))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
internal fun CategoryEditDialog(
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
internal fun CategoryDeleteDialog(
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
internal fun CollectionEditDialog(
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
internal fun CollectionWallsDialog(
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
                                maxLines = 2,
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
internal fun CollectionOrderDialog(
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
                            maxLines = 2,
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
internal fun TagCreateDialog(
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
                            maxLines = 2,
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
internal fun TagEditDialog(
    tag: String,
    wallpapers: List<Wallpaper>,
    categories: List<WallpaperCategory>,
    rule: TagCategory? = null,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
    onClearRule: () -> Unit = {},
) {
    var next by remember(tag) { mutableStateOf(tag) }
    val matching = remember(tag, wallpapers) {
        wallpapers.filter { wallpaper ->
            allTags(wallpaper.tags).any { it.equals(tag, ignoreCase = true) }
        }
    }
    val commonCategoryId = remember(matching) {
        matching.mapNotNull { it.categoryId?.takeIf(String::isNotBlank) }
            .distinct()
            .singleOrNull()
    }
    var categoryId by remember(tag, rule) {
        mutableStateOf(rule?.categoryId ?: commonCategoryId)
    }
    var categoryMenu by remember { mutableStateOf(false) }
    val count = matching.size
    val selectedCategory = categoryId?.let { id -> categories.firstOrNull { it.id == id } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.studio_edit_tag_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text(stringResource(R.string.studio_new_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.studio_tag_category),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { categoryMenu = true },
                        enabled = categories.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = selectedCategory?.name
                                ?: stringResource(R.string.studio_tag_category_none),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenu,
                        onDismissRequest = { categoryMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.studio_tag_category_none)) },
                            onClick = {
                                categoryId = null
                                categoryMenu = false
                            },
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    categoryMenu = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.tag_group_count, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val ruleName = rule?.categoryName
                    ?.takeIf { it.isNotBlank() }
                    ?: rule?.categoryId?.let { id ->
                        categories.firstOrNull { it.id == id }?.name
                    }
                if (ruleName != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.studio_tag_rule_active, ruleName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    TextButton(
                        onClick = onClearRule,
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(stringResource(R.string.studio_tag_rule_clear))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(next, categoryId) },
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
internal fun ConfirmDialog(
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

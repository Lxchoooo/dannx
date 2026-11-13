package com.dannx.walls.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dannx.walls.ErrorKind
import com.dannx.walls.R
import com.dannx.walls.data.StudioEditException
import com.dannx.walls.data.TagsLimit
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.parseTags
import com.dannx.walls.kind
import com.dannx.walls.ui.components.StatefulImage
import com.dannx.walls.ui.components.wallpaperThumbRequest
import com.dannx.walls.ui.theme.LocalLiteMode
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

private const val TAG_MAX_LENGTH = 24
private const val TAG_INPUT_MAX_LENGTH = 240
private const val TITLE_MAX_LENGTH = 120
private const val AUTHOR_MAX_LENGTH = 60

/**
 * Estado del arrastre de la fila de tags: qué chip se mueve, cuánto avanzó y
 * hacia qué posición cruzó el centro; al cruzar dispara el cambio de orden.
 * La lista de posiciones sale siempre del layout vigente de la fila.
 */
private class TagDragState(private val info: () -> LazyListLayoutInfo?) {
    var dragging by mutableIntStateOf(-1)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun start(index: Int) {
        if (dragging == -1) {
            dragging = index
            dragOffset = 0f
        }
    }

    fun drag(index: Int, amount: Offset, onMove: (Int, Int) -> Unit) {
        val from = dragging.takeIf { it >= 0 } ?: return
        dragOffset += amount.x
        val layout = info() ?: return
        val current = layout.visibleItemsInfo.firstOrNull { it.index == from } ?: return
        val movedCenter = current.offset + (current.size / 2f) + dragOffset
        var target = from
        layout.visibleItemsInfo.forEach { other ->
            if (from < other.index && movedCenter > other.offset + other.size / 2f) {
                target = maxOf(target, other.index)
            }
            if (from > other.index && movedCenter < other.offset + other.size / 2f) {
                target = minOf(target, other.index)
            }
        }
        if (target != from) {
            onMove(from, target)
            dragging = target
            dragOffset = 0f
        }
    }

    fun end() {
        dragging = -1
        dragOffset = 0f
    }
}

/**
 * Hoja de edición de un fondo ya publicado: miniatura arriba, título, autor,
 * categoría de la lista o escrita, tags como chips arrastrables y destacado
 * sí/no. Guardar se desactiva sin cambios; si la persona sale con cambios
 * pendientes, pregunta antes de descartarlos. Mientras guarda muestra su
 * propio avance y ante un fallo deja lo escrito para reintentar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WallpaperEditorSheet(
    wallpaper: Wallpaper,
    categories: List<WallpaperCategory>,
    onSave: suspend (
        wallpaperId: String,
        title: String,
        category: String,
        categoryId: String?,
        author: String,
        tags: List<String>,
        featured: Boolean,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf(wallpaper.title) }
    var author by remember { mutableStateOf(wallpaper.author) }
    var categoryName by remember { mutableStateOf(wallpaper.category) }
    var categoryId by remember { mutableStateOf(wallpaper.categoryId) }
    var tags by remember { mutableStateOf(wallpaper.tags) }
    var featured by remember { mutableStateOf(wallpaper.featured) }
    var saving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }

    // Mensajes mapeados antes, porque el guardado corre fuera de la
    // composición y ahí no se pueden resolver recursos.
    val untitledText = stringResource(R.string.studio_untitled)
    val fallbackAuthor = stringResource(R.string.app_name)
    val offlineText = stringResource(R.string.error_offline)
    val serverText = stringResource(R.string.error_server)
    val sessionText = stringResource(R.string.error_session)
    val permissionText = stringResource(R.string.error_permission)
    val unexpectedText = stringResource(R.string.error_unexpected)

    val current = listOf(
        title,
        author,
        categoryName,
        categoryId ?: "",
        tags.joinToString("%"),
        featured.toString(),
    )
    val dirty = current != listOf(
        wallpaper.title,
        wallpaper.author,
        wallpaper.category,
        wallpaper.categoryId ?: "",
        wallpaper.tags.joinToString("%"),
        wallpaper.featured.toString(),
    )

    fun closeOrAsk() {
        if (saving) return
        if (dirty) askDiscard = true else onDismiss()
    }

    fun runSave() {
        if (saving || !dirty) return
        scope.launch {
            saving = true
            errorText = null
            try {
                // El texto de la categoría manda: si coincide con una de
                // la lista usa ese id; si no, queda como categoría nueva.
                val typed = categoryName.trim().take(40)
                val match = categories.firstOrNull {
                    it.name.equals(typed, ignoreCase = true)
                }
                val finalId = when {
                    match != null -> match.id
                    categoryId != null &&
                        categories.any {
                            it.id == categoryId && it.name.equals(typed, ignoreCase = true)
                        } -> categoryId
                    else -> null
                }
                val finalName = match?.name ?: typed
                onSave(
                    wallpaper.id,
                    title.trim().take(TITLE_MAX_LENGTH).ifBlank { untitledText },
                    finalName,
                    finalId,
                    author.trim().take(AUTHOR_MAX_LENGTH).ifBlank { fallbackAuthor },
                    tags,
                    featured,
                )
                onDismiss()
            } catch (error: StudioEditException) {
                errorText = error.message
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                errorText = when (error.kind()) {
                    ErrorKind.OFFLINE -> offlineText
                    ErrorKind.SERVER -> serverText
                    ErrorKind.SESSION -> sessionText
                    ErrorKind.PERMISSION -> permissionText
                    ErrorKind.UNEXPECTED -> unexpectedText
                }
            } finally {
                saving = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { closeOrAsk() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            EditorThumb(wallpaper)
            Text(
                text = stringResource(R.string.studio_edit_wallpaper),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 14.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it.take(TITLE_MAX_LENGTH)
                    errorText = null
                },
                label = { Text(stringResource(R.string.field_title)) },
                supportingText = {
                    Text(stringResource(R.string.studio_title_counter, title.length))
                },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = author,
                onValueChange = {
                    author = it.take(AUTHOR_MAX_LENGTH)
                    errorText = null
                },
                label = { Text(stringResource(R.string.field_author)) },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            CategorySelector(
                categories = categories,
                value = categoryName,
                enabled = !saving,
                onPick = { name, id ->
                    categoryName = name
                    categoryId = id
                    errorText = null
                },
            )
            Spacer(Modifier.height(12.dp))
            EditorTagChips(
                tags = tags,
                enabled = !saving,
                onChange = {
                    tags = it
                    errorText = null
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.studio_featured),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = featured,
                    onCheckedChange = {
                        featured = it
                        errorText = null
                    },
                    enabled = !saving,
                )
            }
            errorText?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { runSave() },
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            if (saving) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = { closeOrAsk() },
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { runSave() },
                    enabled = dirty && !saving,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text(stringResource(R.string.editor_discard_title)) },
            text = { Text(stringResource(R.string.editor_discard_body)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.editor_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { askDiscard = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Miniatura del fondo en el alto de la hoja para revisar qué se edita. */
@Composable
private fun EditorThumb(wallpaper: Wallpaper) {
    val context = LocalContext.current
    val lite = LocalLiteMode.current
    val description = stringResource(R.string.editor_thumb_description)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = description },
    ) {
        StatefulImage(
            model = remember(wallpaper.id, lite) {
                wallpaperThumbRequest(context, wallpaper, lite)
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
    }
}

/** Categoría elegida de la lista abrible o una nueva escrita en el campo. */
@Composable
private fun CategorySelector(
    categories: List<WallpaperCategory>,
    value: String,
    enabled: Boolean,
    onPick: (name: String, id: String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            onPick(typed.take(40), null)
            expanded = true
        },
        label = { Text(stringResource(R.string.field_category)) },
        supportingText = { Text(stringResource(R.string.editor_category_new)) },
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            IconButton(
                onClick = { expanded = true },
                enabled = enabled && categories.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = stringResource(R.string.editor_open_categories),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    DropdownMenu(
        expanded = expanded && categories.isNotEmpty(),
        onDismissRequest = { expanded = false },
    ) {
        categories.forEach { category ->
            DropdownMenuItem(
                text = {
                    Text(category.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                onClick = {
                    expanded = false
                    onPick(category.name, category.id)
                },
            )
        }
    }
}

/**
 * Fila de tags editable: se suman con el botón, se sacan con la cruz del chip
 * y se reordenan arrastrando con una pausa; sin repetidos y hasta 12. El
 * primer chip es el principal y así se lo indica en pantalla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorTagChips(
    tags: List<String>,
    enabled: Boolean,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val drag = remember {
        TagDragState { listState.layoutInfo }
    }
    val haptics = LocalHapticFeedback.current
    val moveTag: (Int, Int) -> Unit = { from, to ->
        onChange(
            tags.toMutableList().apply {
                val item = removeAt(from)
                add(to, item)
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.field_tags),
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(4.dp))
        val cleanDraft = draft.trim().trimStart('#').trim().take(TAG_MAX_LENGTH)
        val additions = parseTags(draft)
            .map { it.take(TAG_MAX_LENGTH) }
            .filterNot { candidate ->
                tags.any { it.equals(candidate, ignoreCase = true) }
            }
            .take((TagsLimit - tags.size).coerceAtLeast(0))
        val duplicate = tags.size < TagsLimit && additions.isEmpty() && cleanDraft.isNotBlank()
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            itemsIndexed(tags, key = { _, tag -> tag.lowercase(Locale.ROOT) }) { index, tag ->
                EditorTagChip(
                    tag = tag,
                    primary = index == 0,
                    dragging = drag.dragging == index,
                    enabled = enabled,
                    onStart = {
                        if (enabled) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag.start(index)
                        }
                    },
                    onDrag = { amount ->
                        if (enabled) drag.drag(index, amount, moveTag)
                    },
                    onEnd = { drag.end() },
                    onRemove = {
                        onChange(tags.filterNot { it.equals(tag, ignoreCase = true) })
                    },
                )
            }
        }
        if (tags.isNotEmpty()) {
            Text(
                text = stringResource(R.string.editor_tags_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(TAG_INPUT_MAX_LENGTH) },
                placeholder = { Text(stringResource(R.string.editor_tag_hint)) },
                singleLine = true,
                enabled = enabled && tags.size < TagsLimit,
                isError = cleanDraft.isNotBlank() && duplicate,
                supportingText = {
                    when {
                        tags.size >= TagsLimit ->
                            Text(stringResource(R.string.editor_tags_limit, TagsLimit))
                        cleanDraft.isNotBlank() && duplicate ->
                            Text(stringResource(R.string.editor_tag_duplicate))
                        parseTags(draft).any { it.length > TAG_MAX_LENGTH } ->
                            Text(stringResource(R.string.editor_tag_length))
                        else -> {}
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (additions.isNotEmpty() && tags.size < TagsLimit) {
                        onChange(tags + additions)
                        draft = ""
                    }
                },
                enabled = enabled && additions.isNotEmpty() && tags.size < TagsLimit,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.editor_add_tag),
                )
            }
        }
    }
}

/** Chip de tag en el editor: arrastrable con pausa y quitable con la cruz. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorTagChip(
    tag: String,
    primary: Boolean,
    dragging: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onRemove: () -> Unit,
) {
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
        modifier = Modifier
            .alpha(if (dragging) 0.55f else 1f)
            .pointerInput(enabled, tag) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { onStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onEnd() },
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (primary) FontWeight.SemiBold else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.editor_remove_tag, tag),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

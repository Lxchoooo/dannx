package com.dannx.walls.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dannx.walls.UiState
import com.dannx.walls.data.DefaultCategories
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCategory
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.parseTags

private enum class StudioSection(
    val label: String,
    val icon: ImageVector,
) {
    PUBLISH("Publicar", Icons.Rounded.AddPhotoAlternate),
    WALLS("Mis fondos", Icons.Rounded.Image),
    CATEGORIES("Categorías", Icons.Rounded.Category),
    TAGS("Tags", Icons.Rounded.LocalOffer),
    COLLECTIONS("Colecciones", Icons.Rounded.Folder),
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
    onDeleteCategory: (String, String?) -> Unit = { _, _ -> },
    onUpdateWallpaper: (String, String, String, String?, List<String>, Boolean) -> Unit =
        { _, _, _, _, _, _ -> },
    onReplaceImage: (String, Uri) -> Unit = { _, _ -> },
    onArchiveWallpaper: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteWallpaper: (String) -> Unit = {},
    onCreateCollection: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onUpdateCollection: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onDeleteCollection: (String) -> Unit = {},
    onAddToCollection: (String, Wallpaper) -> Unit = { _, _ -> },
    onRemoveFromCollection: (String, String) -> Unit = { _, _ -> },
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

    val session = state.session
    LaunchedEffect(session?.userId) {
        if (session != null) onLoadStudio()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
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
                Text("Volver", style = MaterialTheme.typography.labelLarge)
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
                FilledTonalIconButton(onClick = onSignOut) {
                    Icon(Icons.Rounded.Logout, contentDescription = "Cerrar sesión")
                }
            }
        }
        Text(
            text = "Estudio",
            style = MaterialTheme.typography.displayMedium,
        )
        Text(
            text = if (session == null) {
                "Publicá y administrá el catálogo desde un solo lugar."
            } else {
                "Administrá fondos, categorías, tags y colecciones."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        when {
            state.isDemo -> SetupCard(onRefresh = onRefresh)
            session == null -> LoginCard(state = state, onSignIn = onSignIn)
            else -> {
                StudioTabs(
                    selected = section,
                    onSelect = { sectionName = it.name },
                )
                Spacer(Modifier.height(16.dp))
                if (state.studioBusy) {
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
                            text = "Guardando cambios...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                when (section) {
                    StudioSection.PUBLISH -> PublishPanel(
                        state = state,
                        onUpload = onUpload,
                        onSelectUploadUri = onSelectUploadUri,
                    )

                    StudioSection.WALLS -> WallsPanel(
                        state = state,
                        onEdit = { editWallpaper = it },
                        onReplaceImage = onReplaceImage,
                        onArchive = onArchiveWallpaper,
                        onDelete = { deleteWallpaper = it },
                    )

                    StudioSection.CATEGORIES -> CategoriesPanel(
                        state = state,
                        onCreate = onCreateCategory,
                        onEdit = { categoryEditor = it },
                        onDelete = { categoryToDelete = it },
                    )

                    StudioSection.TAGS -> TagsPanel(
                        state = state,
                        onCreate = { showTagCreate = true },
                        onRename = { tagRename = it },
                        onDelete = { tagToDelete = it },
                    )

                    StudioSection.COLLECTIONS -> CollectionsPanel(
                        state = state,
                        onCreate = { collectionEditor = WallpaperCollection(id = "", name = "") },
                        onEdit = { collectionEditor = it },
                        onDelete = { collectionToDelete = it },
                        onManageWalls = { collectionWalls = it },
                        onOrder = { collectionOrder = it },
                        onOpen = onOpenCollection,
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    editWallpaper?.let { wallpaper ->
        WallpaperEditDialog(
            wallpaper = wallpaper,
            state = state,
            onDismiss = { editWallpaper = null },
            onSave = { title, category, categoryId, tags, featured ->
                onUpdateWallpaper(wallpaper.id, title, category, categoryId, tags, featured)
                editWallpaper = null
            },
        )
    }
    deleteWallpaper?.let { wallpaper ->
        ConfirmDialog(
            title = "Borrar fondo",
            text = "Se eliminará \"${wallpaper.title}\" y su imagen de forma permanente. " +
                "También saldrá de las colecciones.",
            confirm = "Borrar",
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
            title = "Borrar colección",
            text = "Se eliminará \"${collection.name}\". Los fondos seguirán publicados.",
            confirm = "Borrar",
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
                val current = collection.wallpaperIds.toSet()
                selectedIds.filterNot { it in current }.forEach { id ->
                    state.studioWallpapers.firstOrNull { it.id == id }?.let { wallpaper ->
                        onAddToCollection(collection.id, wallpaper)
                    }
                }
                current.filterNot { it in selectedIds }.forEach { id ->
                    onRemoveFromCollection(collection.id, id)
                }
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
            title = "Borrar tag",
            text = "Se quitará \"$tag\" de todos los fondos que lo tengan.",
            confirm = "Borrar",
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
                label = { Text(section.label) },
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
                text = "Falta conectar el catálogo",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "La app funciona con fondos de ejemplo. Al conectar el catálogo, " +
                    "el Estudio administra el feed real.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRefresh, shape = CircleShape) {
                Text("Reintentar conexión")
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
                    text = "Solo administradores",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Iniciá sesión con tu cuenta de creador para administrar el catálogo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Correo") },
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
                label = { Text("Contraseña") },
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
                    Text("Entrando...")
                } else {
                    Text("Entrar")
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
        text = "Nuevo fondo",
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
                        Text("Elegir imagen", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "JPG, PNG o WebP hasta 25 MB",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AsyncImage(
                        model = selectedUri,
                        contentDescription = "Vista previa",
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
                        Text("Cambiar imagen")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                placeholder = { Text("Ej. Atardecer en la playa") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(14.dp))
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
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
                label = { Text("Tags") },
                placeholder = { Text("anime, noche, flores") },
                supportingText = {
                    Text("Sepáralos con comas. Los usás para filtrar en Explorar.")
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(14.dp))
            Text("Colección (opcional)", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            if (myCollections.isEmpty()) {
                Text(
                    text = "Creá una colección desde esta sección para agrupar el fondo.",
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
                            label = { Text("Sin colección") },
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
                            selectedCategory?.name ?: "Abstracto",
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
                            Text("Subiendo...")
                        } else {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Publicar fondo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallsPanel(
    state: UiState,
    onEdit: (Wallpaper) -> Unit,
    onReplaceImage: (String, Uri) -> Unit,
    onArchive: (String, Boolean) -> Unit,
    onDelete: (Wallpaper) -> Unit,
) {
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var replacingId by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val id = replacingId
        if (uri != null && id != null) onReplaceImage(id, uri)
        replacingId = null
    }
    val walls = state.studioWallpapers.filter { it.isArchived == showArchived }
    val archivedCount = state.studioWallpapers.count { it.isArchived }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Mis fondos",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = showArchived,
            onClick = { showArchived = !showArchived },
            label = { Text(if (showArchived) "Archivados" else "Publicados") },
            leadingIcon = {
                Icon(
                    imageVector = if (showArchived) Icons.Rounded.Archive else Icons.Rounded.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            shape = CircleShape,
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (showArchived) {
            "$archivedCount archivados"
        } else {
            "${state.studioWallpapers.size - archivedCount} publicados"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (walls.isEmpty()) {
        Text(
            text = if (showArchived) "No hay fondos archivados." else "Todavía no publicaste fondos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    walls.forEach { wallpaper ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
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
                        text = state.categoryNameFor(wallpaper),
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
                            text = "Archivado",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row {
                        IconButton(onClick = { onEdit(wallpaper) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Editar")
                        }
                        IconButton(
                            onClick = {
                                replacingId = wallpaper.id
                                picker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Imagen")
                        }
                    }
                    Row {
                        IconButton(onClick = { onArchive(wallpaper.id, !wallpaper.isArchived) }) {
                            Icon(
                                imageVector = if (wallpaper.isArchived) {
                                    Icons.Rounded.Unarchive
                                } else {
                                    Icons.Rounded.Archive
                                },
                                contentDescription = if (wallpaper.isArchived) {
                                    "Restaurar"
                                } else {
                                    "Archivar"
                                },
                            )
                        }
                        IconButton(onClick = { onDelete(wallpaper) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Borrar",
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
    onDelete: (WallpaperCategory) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Text("Categorías", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Organizá los fondos en Explorar. Una categoría con fondos no se puede borrar sin reasignarlos.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.weight(1f),
            label = { Text("Nueva categoría") },
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
            Icon(Icons.Rounded.Add, contentDescription = "Crear categoría")
        }
    }
    Spacer(Modifier.height(10.dp))
    if (state.categories.isEmpty()) {
        Text(
            text = "Todavía no hay categorías. Creá la primera.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    state.categories.forEach { category ->
        val count = state.studioWallpapers.count { it.categoryId == category.id }
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
                    text = if (count == 1) "1 fondo" else "$count fondos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onEdit(category) }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Renombrar")
            }
            IconButton(onClick = { onDelete(category) }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Borrar",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        HorizontalDivider()
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
            text = "Tags",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onCreate, shape = CircleShape) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Crear tag")
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Los tags se aplican a los fondos que elijas y se pueden renombrar o unir.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (counts.isEmpty()) {
        Text(
            text = "Todavía no hay tags.",
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
                    text = if (count == 1) "1 fondo" else "$count fondos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRename(tag) }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Renombrar")
            }
            IconButton(onClick = { onDelete(tag) }) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Borrar",
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
            text = "Colecciones",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onCreate, shape = CircleShape) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Crear")
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Cambiá el nombre, la visibilidad y los fondos de cada colección.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    if (state.myCollections.isEmpty()) {
        Text(
            text = "Todavía no tenés colecciones.",
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
                        Text(
                            text = "${collection.wallpaperIds.size} fondos · " +
                                if (collection.isPublic) "Pública" else "Privada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onEdit(collection) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar")
                    }
                    IconButton(onClick = { onDelete(collection) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Borrar",
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
                        Text("Fondos")
                    }
                    OutlinedButton(onClick = { onOrder(collection) }) {
                        Text("Orden")
                    }
                    OutlinedButton(onClick = { onOpen(collection) }) {
                        Text("Abrir")
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperEditDialog(
    wallpaper: Wallpaper,
    state: UiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, List<String>, Boolean) -> Unit,
) {
    var title by remember(wallpaper.id) { mutableStateOf(wallpaper.title) }
    var tags by remember(wallpaper.id) { mutableStateOf(wallpaper.tags.joinToString(", ")) }
    var featured by remember(wallpaper.id) { mutableStateOf(wallpaper.featured) }
    var categoryId by remember(wallpaper.id) { mutableStateOf(wallpaper.categoryId) }
    val options = remember(wallpaper, state.categories) {
        val base = state.categories
        if (wallpaper.categoryId != null && base.none { it.id == wallpaper.categoryId }) {
            base + WallpaperCategory(
                id = wallpaper.categoryId,
                name = wallpaper.category.ifBlank { "Sin categoría" },
            )
        } else {
            base
        }
    }
    val selected = options.firstOrNull { it.id == categoryId }
        ?: options.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar fondo") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
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
                    label = { Text("Tags") },
                    supportingText = { Text("Separados por comas.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Destacado", modifier = Modifier.weight(1f))
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
                        parseTags(tags),
                        featured,
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text(if (isNew) "Nueva categoría" else "Renombrar categoría") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text("Borrar categoría") },
        text = {
            Column {
                Text(
                    if (count == 0) {
                        "Se eliminará \"${category.name}\"."
                    } else {
                        "\"${category.name}\" tiene $count fondos. Elegí una categoría de destino."
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
                            text = "Creá otra categoría para poder mover estos fondos.",
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
                Text("Borrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text(if (isNew) "Nueva colección" else "Editar colección") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pública", modifier = Modifier.weight(1f))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, description, isPublic) },
                enabled = name.isNotBlank(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selected.value = wallpapers.map { it.id }.toSet()
                    }) {
                        Text("Todos")
                    }
                    TextButton(onClick = { selected.value = emptySet() }) {
                        Text("Ninguno")
                    }
                }
                wallpapers.forEach { wallpaper ->
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
                                    "Archivado",
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
            TextButton(onClick = { onSave(selected.value) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text("Orden de ${collection.name}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                order.forEachIndexed { index, id ->
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
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Subir")
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
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Bajar")
                        }
                    }
                }
                if (order.isEmpty()) {
                    Text(
                        text = "La colección no tiene fondos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(order) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text("Crear tag") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Nombre del tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Aplicar a", style = MaterialTheme.typography.labelLarge)
                wallpapers.forEach { wallpaper ->
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
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text("Renombrar tag") },
        text = {
            OutlinedTextField(
                value = next,
                onValueChange = { next = it },
                label = { Text("Nombre nuevo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(next) },
                enabled = next.isNotBlank(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
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
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

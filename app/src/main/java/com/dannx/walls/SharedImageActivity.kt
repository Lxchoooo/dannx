package com.dannx.walls

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dannx.walls.ui.theme.DannXTheme
import com.dannx.walls.util.WallpaperFiles
import com.dannx.walls.util.WallpaperTarget
import kotlinx.coroutines.launch

/** Recibe una o varias imágenes desde el menú Compartir de Android. */
class SharedImageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val imageUris = intent?.sharedImageUris().orEmpty()
        setContent {
            DannXTheme {
                SharedImageScreen(imageUris = imageUris, onClose = { finish() })
            }
        }
    }
}

private sealed interface ApplyResult {
    data class Done(val applied: Int, val total: Int) : ApplyResult
    data object Failed : ApplyResult
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SharedImageScreen(imageUris: List<Uri>, onClose: () -> Unit) {
    val context = LocalContext.current
    val files = remember(context) { WallpaperFiles(context) }
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf(WallpaperTarget.BOTH) }
    var applying by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ApplyResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shared_image_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (imageUris.isEmpty()) {
                Text(
                    text = stringResource(R.string.shared_image_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = onClose) {
                    Text(stringResource(R.string.action_close))
                }
            } else {
                SharedImagePreview(imageUris)
                Text(
                    text = if (imageUris.size > 1) {
                        stringResource(R.string.shared_image_body_many, imageUris.size)
                    } else {
                        stringResource(R.string.shared_image_body)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.apply_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WallpaperTarget.entries.forEach { option ->
                        FilterChip(
                            selected = target == option,
                            onClick = { target = option },
                            label = { Text(stringResource(targetLabel(option))) },
                        )
                    }
                }
                Button(
                    onClick = {
                        applying = true
                        result = null
                        scope.launch {
                            result = runCatching {
                                files.setAsWallpaper(imageUris, target)
                            }.fold(
                                onSuccess = { ApplyResult.Done(it, imageUris.size) },
                                onFailure = { ApplyResult.Failed },
                            )
                            applying = false
                        }
                    },
                    enabled = !applying,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (applying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.shared_image_applying),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    } else {
                        Text(stringResource(R.string.shared_image_apply))
                    }
                }
                result?.let { outcome ->
                    val success = outcome is ApplyResult.Done &&
                        outcome.applied == outcome.total
                    Text(
                        text = when (outcome) {
                            is ApplyResult.Done -> if (outcome.total > 1) {
                                stringResource(
                                    R.string.shared_image_applied_many,
                                    outcome.applied,
                                    outcome.total,
                                )
                            } else {
                                stringResource(R.string.shared_image_applied)
                            }
                            ApplyResult.Failed -> stringResource(R.string.shared_image_error)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (success) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedImagePreview(imageUris: List<Uri>) {
    val description = stringResource(R.string.shared_image_preview)
    if (imageUris.size == 1) {
        AsyncImage(
            model = imageUris.first(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(24.dp)),
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            imageUris.take(3).forEach { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
    }
}

private fun targetLabel(target: WallpaperTarget): Int = when (target) {
    WallpaperTarget.SYSTEM -> R.string.apply_system
    WallpaperTarget.LOCK -> R.string.apply_lock
    WallpaperTarget.BOTH -> R.string.apply_both
}

private fun Intent.sharedImageUris(): List<Uri> {
    val uris = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(streamUri())
        Intent.ACTION_SEND_MULTIPLE -> streamUris()
        Intent.ACTION_VIEW -> listOfNotNull(data)
        else -> emptyList()
    }
    return uris.distinct()
}

private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }

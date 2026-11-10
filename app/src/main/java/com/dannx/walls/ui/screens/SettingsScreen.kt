package com.dannx.walls.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dannx.walls.BuildConfig
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.ui.components.UpdateDialog
import com.dannx.walls.ui.update.UpdateState
import com.dannx.walls.ui.update.UpdateViewModel
import com.dannx.walls.util.AppLinks
import com.dannx.walls.util.formatFileSize
import com.dannx.walls.util.openUrl

/**
 * Ajustes de la app: apariencia, caché y datos generales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLiteModeChange: (Boolean) -> Unit,
    onOpenEditorOnApplyChange: (Boolean) -> Unit,
    onRefreshCacheSize: () -> Unit,
    onClearCache: () -> Unit,
    onOpenAbout: () -> Unit,
    onResetAppearance: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val updateViewModel: UpdateViewModel = viewModel(
        factory = UpdateViewModel.factory(application),
    )
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        onRefreshCacheSize()
        updateViewModel.checkInBackground(BuildConfig.VERSION_NAME)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_appearance)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_theme_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = state.themeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThemeMode.entries.size,
                                    ),
                                    label = { Text(stringResource(mode.labelRes())) },
                                )
                            }
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.settings_language_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { openAppLanguageSettings(context) }) {
                                Text(stringResource(R.string.action_change))
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.liteMode,
                                role = Role.Switch,
                                onValueChange = onLiteModeChange,
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_lite), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.settings_lite_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.liteMode,
                            onCheckedChange = null,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = state.openEditorOnApply,
                                role = Role.Switch,
                                onValueChange = onOpenEditorOnApplyChange,
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_open_editor),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.settings_open_editor_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = state.openEditorOnApply,
                            onCheckedChange = null,
                        )
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_storage)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_cache), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.settings_cache_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_cache_used,
                                    formatFileSize(state.cacheSizeBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onClearCache) {
                            Text(stringResource(R.string.action_clear))
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_credits)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.settings_made_by),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_license),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = {
                                updateViewModel.check(BuildConfig.VERSION_NAME, force = true)
                            },
                        ) {
                            Text(stringResource(R.string.settings_check_updates))
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.Repo) }) {
                            Text(stringResource(R.string.settings_github))
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        TextButton(onClick = onOpenAbout) {
                            Text(stringResource(R.string.settings_about))
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.privacy) }) {
                            Text(stringResource(R.string.settings_privacy))
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.settings_advanced)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        TextButton(onClick = onResetAppearance) {
                            Text(stringResource(R.string.settings_reset))
                        }
                        if (state.session != null) {
                            TextButton(
                                onClick = onSignOut,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Text(stringResource(R.string.settings_sign_out))
                            }
                        }
                    }
                }
            }
        }
    }

    if (updateState !is UpdateState.Idle) {
        UpdateDialog(
            state = updateState,
            onDismiss = updateViewModel::dismiss,
            onDownload = updateViewModel::download,
            onCancelDownload = updateViewModel::cancelDownload,
            onInstall = { updateViewModel.install(context) },
            onOpenRelease = { release ->
                openUrl(context, release.htmlUrl.ifBlank { AppLinks.Releases })
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun openAppLanguageSettings(context: Context) {
    val packageUri = "package:${context.packageName}".toUri()
    val localeIntent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, packageUri)
    if (runCatching { context.startActivity(localeIntent) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

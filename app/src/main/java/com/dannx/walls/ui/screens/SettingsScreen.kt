package com.dannx.walls.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dannx.walls.BuildConfig
import com.dannx.walls.R
import com.dannx.walls.UiState
import com.dannx.walls.data.ColorContrast
import com.dannx.walls.data.ColorPaletteStyle
import com.dannx.walls.data.LiveMotion
import com.dannx.walls.data.ThemeMode
import com.dannx.walls.ui.components.ColorSettingsCard
import com.dannx.walls.ui.components.SettingsCard
import com.dannx.walls.ui.components.SettingsCardHeader
import com.dannx.walls.ui.components.SettingsIcon
import com.dannx.walls.ui.components.SettingsRow
import com.dannx.walls.ui.components.StaggeredAppear
import com.dannx.walls.ui.components.UpdateDialog
import com.dannx.walls.ui.update.UpdateState
import com.dannx.walls.ui.update.UpdateViewModel
import com.dannx.walls.util.AppLinks
import com.dannx.walls.util.formatFileSize
import com.dannx.walls.util.openUrl

/**
 * Ajustes de la app: apariencia, comportamiento, caché y datos generales.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    updateViewModel: UpdateViewModel,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLiteModeChange: (Boolean) -> Unit,
    onLiveMotionChange: (LiveMotion) -> Unit,
    onOpenEditorOnApplyChange: (Boolean) -> Unit,
    onColorSeedChange: (String) -> Unit,
    onPaletteStyleChange: (ColorPaletteStyle) -> Unit,
    onContrastLevelChange: (ColorContrast) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    onRefreshCacheSize: () -> Unit,
    onClearCache: () -> Unit,
    onOpenAbout: () -> Unit,
    onResetAppearance: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        onRefreshCacheSize()
        updateViewModel.checkInBackground(BuildConfig.VERSION_NAME)
    }
    // Las dos acciones destructivas de la pantalla confirman antes: borrar la
    // caché obliga a volver a descargar, y restablecer pierde la apariencia
    // elegida.
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }
    var confirmResetAppearance by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
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
        val haptics = LocalHapticFeedback.current
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
            staggeredItem(0) { SectionTitle(stringResource(R.string.settings_appearance)) }
            staggeredItem(1) {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        SettingsCardHeader(
                            icon = Icons.Rounded.LightMode,
                            title = stringResource(R.string.settings_theme),
                            hint = stringResource(R.string.settings_theme_hint),
                        )
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = state.themeMode == mode,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onThemeModeChange(mode)
                                    },
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
                staggeredItem(2) {
                    SettingsCard {
                        SettingsRow(
                            icon = Icons.Rounded.Language,
                            title = stringResource(R.string.settings_language),
                            hint = stringResource(R.string.settings_language_hint),
                        ) {
                            TextButton(onClick = { openAppLanguageSettings(context) }) {
                                Text(stringResource(R.string.action_change))
                            }
                        }
                    }
                }
            }
            staggeredItem(3) {
                ColorSettingsCard(
                    colorSeed = state.colorSeed,
                    paletteStyle = state.paletteStyle,
                    contrastLevel = state.contrastLevel,
                    amoled = state.amoled,
                    darkTheme = darkTheme,
                    onColorSeedChange = onColorSeedChange,
                    onPaletteStyleChange = onPaletteStyleChange,
                    onContrastLevelChange = onContrastLevelChange,
                    onAmoledChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAmoledChange(it)
                    },
                )
            }
            staggeredItem(4) { SectionTitle(stringResource(R.string.settings_behavior)) }
            staggeredItem(5) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.settings_lite),
                        hint = stringResource(R.string.settings_lite_hint),
                        checked = state.liteMode,
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLiteModeChange(it)
                        },
                    )
                }
            }

            staggeredItem(6) {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(Icons.Rounded.Animation)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_live_motion),
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                                Text(
                                    stringResource(R.string.settings_live_motion_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            LiveMotion.entries.forEachIndexed { index, motion ->
                                SegmentedButton(
                                    selected = state.liveMotion == motion,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onLiveMotionChange(motion)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = LiveMotion.entries.size,
                                    ),
                                    label = { Text(stringResource(motion.labelRes())) },
                                )
                            }
                        }
                        if (state.liteMode) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.settings_live_motion_lite),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            staggeredItem(7) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.settings_open_editor),
                        hint = stringResource(R.string.settings_open_editor_hint),
                        checked = state.openEditorOnApply,
                        onCheckedChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenEditorOnApplyChange(it)
                        },
                    )
                }
            }

            staggeredItem(8) { SectionTitle(stringResource(R.string.settings_storage)) }
            staggeredItem(9) {
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Rounded.CleaningServices,
                        title = stringResource(R.string.settings_cache),
                        hint = stringResource(R.string.settings_cache_hint),
                        extra = {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(
                                    R.string.settings_cache_used,
                                    formatFileSize(state.cacheSizeBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailing = {
                            TextButton(onClick = { confirmClearCache = true }) {
                                Text(stringResource(R.string.action_clear))
                            }
                        },
                    )
                }
            }

            staggeredItem(10) { SectionTitle(stringResource(R.string.settings_credits)) }
            staggeredItem(11) {
                SettingsCard {
                    Column(Modifier.padding(16.dp)) {
                        SettingsCardHeader(
                            icon = Icons.Rounded.Info,
                            title = stringResource(R.string.app_name),
                            hint = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
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

            staggeredItem(12) { SectionTitle(stringResource(R.string.settings_advanced)) }
            staggeredItem(13) {
                SettingsCard {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        TextButton(onClick = { confirmResetAppearance = true }) {
                            Text(stringResource(R.string.settings_reset))
                        }
                        if (state.session != null) {
                            TextButton(
                                onClick = { confirmSignOut = true },
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

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.settings_cache_clear_title)) },
            text = { Text(stringResource(R.string.settings_cache_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCache = false
                        onClearCache()
                    },
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmResetAppearance) {
        AlertDialog(
            onDismissRequest = { confirmResetAppearance = false },
            title = { Text(stringResource(R.string.settings_reset)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmResetAppearance = false
                        onResetAppearance()
                    },
                ) {
                    Text(stringResource(R.string.settings_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAppearance = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                ) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = 8.dp, start = 4.dp)
            .semantics { heading() },
    )
}

/**
 * Ítem de Ajustes que entra con la misma aparición escalonada de las grillas:
 * los primeros se acomodan apenas se abre la pantalla y el resto aparece
 * directo. El índice es el de la lista, así que conviene renumerarlo al
 * reordenar los ítems.
 */
private fun LazyListScope.staggeredItem(index: Int, content: @Composable () -> Unit) {
    item {
        StaggeredAppear(index = index) { content() }
    }
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

private fun LiveMotion.labelRes(): Int = when (this) {
    LiveMotion.SUBTLE -> R.string.live_motion_subtle
    LiveMotion.NORMAL -> R.string.live_motion_normal
    LiveMotion.STILL -> R.string.live_motion_still
}

package com.dannx.walls.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dannx.walls.R
import com.dannx.walls.data.update.ReleaseInfo
import com.dannx.walls.ui.update.UpdateState

/**
 * Diálogo único del actualizador. Mantiene el flujo manual: consultar,
 * descargar, verificar e instalar, sin chequeos en segundo plano.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateDialog(
    state: UpdateState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: (ReleaseInfo) -> Unit,
) {
    val title = when (state) {
        UpdateState.Idle -> return
        UpdateState.Checking -> stringResource(R.string.settings_check_updates)
        is UpdateState.UpToDate -> stringResource(R.string.settings_check_updates)
        is UpdateState.Available -> stringResource(
            R.string.update_available_title,
            state.release.version,
        )
        is UpdateState.Downloading -> stringResource(R.string.update_downloading_title)
        is UpdateState.Ready -> stringResource(
            R.string.update_available_title,
            state.release.version,
        )
        is UpdateState.Permission -> stringResource(
            R.string.update_available_title,
            state.release.version,
        )
        is UpdateState.Error -> stringResource(R.string.update_error_title)
    }
    AlertDialog(
        onDismissRequest = when (state) {
            is UpdateState.Downloading -> onCancelDownload
            else -> onDismiss
        },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (state) {
                    UpdateState.Idle, UpdateState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(22.dp))
                            Text(
                                text = stringResource(R.string.update_checking),
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }

                    is UpdateState.UpToDate -> Text(
                        text = stringResource(R.string.update_up_to_date),
                    )

                    is UpdateState.Available -> {
                        Text(text = stringResource(R.string.update_available_body))
                        val notes = remember(state.release.notes) {
                            state.release.notes.toPlainReleaseNotes()
                        }
                        if (notes.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.update_notes),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Text(
                            text = stringResource(
                                R.string.update_downloading,
                                state.progress,
                            ),
                        )
                        Spacer(Modifier.height(10.dp))
                        LinearWavyProgressIndicator(
                            progress = { state.progress.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is UpdateState.Ready -> Text(text = stringResource(R.string.update_ready))

                    is UpdateState.Permission -> Text(
                        text = stringResource(R.string.update_permission),
                    )

                    is UpdateState.Error -> Text(text = state.message)
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }

                is UpdateState.Downloading -> TextButton(onClick = onCancelDownload) {
                    Text(stringResource(R.string.action_cancel))
                }

                is UpdateState.Ready -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_install))
                }

                is UpdateState.Permission -> TextButton(onClick = onInstall) {
                    Text(stringResource(R.string.update_permission_action))
                }

                is UpdateState.Error -> {
                    val release = state.release
                    if (release != null) {
                        TextButton(onClick = { onOpenRelease(release) }) {
                            Text(stringResource(R.string.update_open_github))
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.update_close))
                        }
                    }
                }

                UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_close))
                    }
                }
            }
        },
        dismissButton = {
            when (state) {
                is UpdateState.Available -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later))
                }

                is UpdateState.Downloading -> Unit

                is UpdateState.Ready,
                is UpdateState.Permission,
                is UpdateState.Error -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_close))
                }

                UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate -> Unit
            }
        },
    )
}

private fun String.toPlainReleaseNotes(): String = this
    .replace(Regex("\\*\\*(.*?)\\*\\*"), "\$1")
    .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
    .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "• ")
    .trim()

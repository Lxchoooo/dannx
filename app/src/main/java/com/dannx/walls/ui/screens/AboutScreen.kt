package com.dannx.walls.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dannx.walls.BuildConfig
import com.dannx.walls.R
import com.dannx.walls.util.AppLinks
import com.dannx.walls.util.openUrl

/**
 * Información de la app, enlaces públicos y avisos de licencias. No usa
 * dependencias nuevas: las licencias se listan de forma estática.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicenses by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(
                                R.string.about_version,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.about_description),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item { AboutSectionTitle(stringResource(R.string.about_project)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        LinkRow(stringResource(R.string.about_repository)) {
                            openUrl(context, AppLinks.Repo)
                        }
                        LinkRow(stringResource(R.string.about_releases)) {
                            openUrl(context, AppLinks.Releases)
                        }
                        LinkRow(stringResource(R.string.about_privacy)) {
                            openUrl(context, AppLinks.privacy)
                        }
                        LinkRow(
                            label = stringResource(R.string.about_share),
                            icon = Icons.Rounded.Share,
                        ) {
                            shareApp(context)
                        }
                    }
                }
            }

            item { AboutSectionTitle(stringResource(R.string.about_legal)) }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.about_license),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.about_license_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showLicenses = true }) {
                            Text(stringResource(R.string.about_licenses))
                        }
                        TextButton(onClick = { openUrl(context, AppLinks.License) }) {
                            Text(stringResource(R.string.about_view_license))
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
        }
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun LinkRow(
    label: String,
    icon: ImageVector = Icons.Rounded.OpenInNew,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Start,
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            context.getString(R.string.about_share_text, AppLinks.Repo),
        )
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.about_share)),
        )
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_licenses)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    stringResource(R.string.about_licenses_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                LicenseLine("Kotlin")
                LicenseLine("AndroidX / Jetpack Compose")
                LicenseLine("Material 3")
                LicenseLine("Coil")
                LicenseLine("OkHttp")
                LicenseLine("kotlinx.coroutines")
                LicenseLine("kotlinx.serialization")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun LicenseLine(name: String) {
    Text(
        text = stringResource(R.string.about_license_line, name),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

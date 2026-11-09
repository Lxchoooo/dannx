package com.dannx.walls.ui.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dannx.walls.R
import com.dannx.walls.data.update.AppVersion
import com.dannx.walls.data.update.GitHubUpdateRepository
import com.dannx.walls.data.update.ReleaseInfo
import com.dannx.walls.data.update.UpdateError
import com.dannx.walls.data.update.UpdateRepository
import com.dannx.walls.util.StringResolver
import com.dannx.walls.util.get
import com.dannx.walls.util.stringResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val strings: StringResolver,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var downloadingRelease: ReleaseInfo? = null

    fun check(currentVersion: String) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = repository.latestRelease()
                val latest = AppVersion.parse(release.version)
                    ?: throw UpdateError.Format
                val current = AppVersion.parse(currentVersion)
                if (current != null && !latest.isNewerThan(current)) {
                    _state.value = UpdateState.UpToDate(currentVersion)
                } else {
                    _state.value = UpdateState.Available(release)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = UpdateState.Error(error.messageForUser())
            }
        }
    }

    fun download() {
        val available = _state.value as? UpdateState.Available ?: return
        downloadJob?.cancel()
        downloadingRelease = available.release
        downloadJob = viewModelScope.launch {
            _state.value = UpdateState.Downloading(0)
            try {
                val file = repository.downloadVerified(available.release) { downloaded, total ->
                    val progress = if (total > 0) {
                        ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    } else {
                        0
                    }
                    _state.value = UpdateState.Downloading(progress)
                }
                downloadingRelease = null
                _state.value = UpdateState.Ready(file, available.release)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                downloadingRelease = null
                _state.value = UpdateState.Error(
                    message = error.messageForUser(),
                    release = available.release,
                )
            }
        }
    }

    fun cancelDownload() {
        val release = downloadingRelease ?: return
        downloadingRelease = null
        downloadJob?.cancel()
        _state.value = UpdateState.Available(release)
    }

    fun dismiss() {
        checkJob?.cancel()
        downloadJob?.cancel()
        downloadingRelease = null
        _state.value = UpdateState.Idle
    }

    /**
     * Abre el instalador del sistema. Android exige que el usuario habilite
     * “instalar apps desconocidas” para DannX antes de aceptar el APK.
     */
    fun install(context: Context) {
        val current = _state.value
        val file = when (current) {
            is UpdateState.Ready -> current.file
            is UpdateState.Permission -> current.file
            else -> return
        }
        val release = when (current) {
            is UpdateState.Ready -> current.release
            is UpdateState.Permission -> current.release
            else -> return
        }
        val appContext = context.applicationContext
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.Permission(file, release)
            runCatching {
                appContext.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure {
                _state.value = UpdateState.Error(
                    strings.get(R.string.update_error_install),
                    release,
                )
            }
    }

    private fun Throwable.messageForUser(): String = when (this) {
        is UpdateError.Network -> strings.get(R.string.update_error_network)
        is UpdateError.NoRelease -> strings.get(R.string.update_error_no_release)
        is UpdateError.Format -> strings.get(R.string.update_error_format)
        is UpdateError.MissingAsset -> strings.get(R.string.update_error_asset)
        is UpdateError.Checksum -> strings.get(R.string.update_error_checksum)
        is UpdateError.Download -> strings.get(R.string.update_error_download)
        else -> strings.get(R.string.update_error_download)
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"

        fun factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UpdateViewModel(
                    repository = GitHubUpdateRepository(application),
                    strings = application.stringResolver(),
                )
            }
        }
    }
}

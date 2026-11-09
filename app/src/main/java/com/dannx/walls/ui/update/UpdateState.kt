package com.dannx.walls.ui.update

import com.dannx.walls.data.update.ReleaseInfo
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val current: String) : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val progress: Int) : UpdateState
    data class Ready(val file: File, val release: ReleaseInfo) : UpdateState
    data class Permission(val file: File, val release: ReleaseInfo) : UpdateState
    data class Error(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

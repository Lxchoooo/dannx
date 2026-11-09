package com.dannx.walls.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale

/**
 * Enlaces públicos de la app, centralizados para que Ajustes y Acerca de
 * apunten siempre al mismo lugar.
 */
object AppLinks {
    const val Repo = "https://github.com/Lxchoooo/dannx"
    const val Releases = "https://github.com/Lxchoooo/dannx/releases/latest"
    const val License = "https://github.com/Lxchoooo/dannx/blob/main/LICENSE"
    const val Security = "https://github.com/Lxchoooo/dannx/blob/main/SECURITY.md"

    val privacy: String
        get() = if (Locale.getDefault().language == "es") {
            "https://github.com/Lxchoooo/dannx/blob/main/PRIVACY.es.md"
        } else {
            "https://github.com/Lxchoooo/dannx/blob/main/PRIVACY.md"
        }
}

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}

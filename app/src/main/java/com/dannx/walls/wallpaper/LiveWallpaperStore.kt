package com.dannx.walls.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.core.content.edit
import java.io.File

/**
 * Imagen que dibuja [DannXWallpaperService] y el aviso de que cambió.
 *
 * El servicio vive en este mismo proceso, así que alcanza con dejar la foto en
 * un archivo privado y subir un contador en preferencias: el servicio escucha
 * ese cambio y vuelve a cargar la imagen sin reiniciarse.
 */
internal class LiveWallpaperStore(private val context: Context) {

    fun imageFile(): File = File(File(context.filesDir, Folder), FileName)

    /**
     * Escribe la foto en un temporal y la renombra al final. El servicio nunca
     * llega a leer un archivo a medio escribir.
     */
    fun save(bitmap: Bitmap) {
        val target = imageFile()
        val folder = target.parentFile ?: error("Sin carpeta para el fondo animado")
        folder.mkdirs()
        val temp = File(folder, "$FileName.part")
        try {
            temp.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)) {
                    "No se pudo escribir el fondo animado"
                }
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
            }
        } finally {
            temp.delete()
        }
        prefs(context).edit { putLong(KEY_VERSION, System.currentTimeMillis()) }
    }

    /** Si el fondo animado que está puesto es el nuestro. */
    fun isActive(): Boolean {
        val info = runCatching {
            WallpaperManager.getInstance(context).wallpaperInfo
        }.getOrNull()
        return info?.component == ComponentName(context, DannXWallpaperService::class.java)
    }

    /** Abre el selector del sistema con este servicio ya elegido. */
    fun chooserIntent(): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, DannXWallpaperService::class.java),
            )
        }

    companion object {
        const val KEY_VERSION = "version"

        private const val PrefsName = "dannx_live_wallpaper"
        private const val Folder = "live"
        private const val FileName = "current.jpg"
        private const val JpegQuality = 94

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    }
}

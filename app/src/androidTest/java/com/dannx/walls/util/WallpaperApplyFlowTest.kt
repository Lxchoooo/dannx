package com.dannx.walls.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import com.dannx.walls.data.Wallpaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flujo crítico de aplicar un fondo: descarga real por HTTP local, recorte al
 * tamaño de pantalla y destino. El sistema de fondos se reemplaza con un
 * [WallpaperApplier] de prueba: el emulador no cambia su fondo y no hace falta
 * ningún permiso extra.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperApplyFlowTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun applyingToHomeScreenCropsToScreenSizeAndUsesSystemTarget() = runBlocking {
        val server = PngServer(pngBytes(width = 600, height = 300))
        val applier = RecordingApplier()
        val files = WallpaperFiles(context, applier)
        val wallpaper = Wallpaper(
            id = "flow-1",
            title = "Flujo",
            category = "Prueba",
            imageUrl = server.url(),
        )
        try {
            val result = files.setAsWallpaper(wallpaper, WallpaperTarget.SYSTEM)

            assertEquals(WallpaperTarget.SYSTEM, result)
            assertEquals(WallpaperTarget.SYSTEM, applier.target)

            val expected = wallpaperTargetSize(context)
            assertEquals(expected.x, applier.width)
            assertEquals(expected.y, applier.height)
            assertTrue("La imagen aplicada quedó vacía", applier.alpha != 0)
        } finally {
            server.stop()
        }

        // El temporal se borra siempre, incluso si el sistema de fondos falla.
        val temporaryFolder = File(context.cacheDir, "wallpaper-tmp")
        assertTrue(temporaryFolder.listFiles().isNullOrEmpty())
    }

    /**
     * Guarda lo que recibe el sistema de fondos. No se puede conservar el
     * bitmap porque el flujo lo recicla apenas termina.
     */
    private class RecordingApplier : WallpaperApplier {
        var target: WallpaperTarget? = null
        var width = 0
        var height = 0
        var alpha = 0

        override fun apply(bitmap: Bitmap, target: WallpaperTarget): WallpaperTarget {
            this.target = target
            width = bitmap.width
            height = bitmap.height
            alpha = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) ushr 24
            return target
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30, 60, 120))
        canvas.drawRect(
            0f,
            0f,
            width / 2f,
            height.toFloat(),
            Paint().apply { color = Color.rgb(210, 80, 60) },
        )
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    /**
     * Servidor HTTP mínimo en loopback: responde una imagen y se apaga. Evita
     * depender de la red o de un backend para probar la aplicación del fondo.
     */
    private class PngServer(private val bytes: ByteArray) {
        private val socket = ServerSocket(
            0,
            1,
            InetAddress.getByName("127.0.0.1"),
        )
        private val worker = thread(name = "png-server") {
            runCatching {
                socket.soTimeout = 10_000
                socket.accept().use { client ->
                    client.soTimeout = 10_000
                    val input = client.getInputStream().bufferedReader()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: image/png\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    client.getOutputStream().apply {
                        write(header.toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }.onFailure { Log.w("WallpaperApplyFlowTest", "Servidor de prueba", it) }
        }

        fun url(): String = "http://127.0.0.1:${socket.localPort}/wall.png"

        fun stop() {
            runCatching { socket.close() }
            worker.join(2_000)
        }
    }
}

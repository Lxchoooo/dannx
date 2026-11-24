package com.dannx.walls.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Paquete de la app que se mide. */
internal const val TARGET_PACKAGE = "com.dannx.walls"

/** Iteraciones por prueba: el mínimo que usa la guía de Macrobenchmark. */
internal const val ITERATIONS = 5

/**
 * La primera instalación abre la bienvenida, y sus pantallas no son lo que la
 * persona usa todos los días. Benchmarks y generador de perfil tienen que salir
 * de ahí antes de medir o perfilar el feed.
 */
internal fun MacrobenchmarkScope.skipOnboardingIfNeeded() {
    val visible = device.wait(Until.hasObject(By.text("Skip")), 2_000) ||
        device.wait(Until.hasObject(By.text("Omitir")), 1_000)
    if (!visible) return
    (device.findObject(By.text("Skip")) ?: device.findObject(By.text("Omitir")))?.click()
    device.waitForIdle()
}

/**
 * Desliza la grilla del feed sin depender de un `testTag`: el gesto cae sobre
 * el centro de la pantalla, así que también funciona mientras el feed carga o
 * cuando está vacío.
 */
internal fun MacrobenchmarkScope.scrollFeed(times: Int = 3) {
    repeat(times) {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            device.displayWidth / 2,
            device.displayHeight / 4,
            20,
        )
        device.waitForIdle()
    }
}

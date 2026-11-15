package com.dannx.walls.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope

/** Paquete de la app que se mide. */
internal const val TARGET_PACKAGE = "com.dannx.walls"

/** Iteraciones por prueba: el mínimo que usa la guía de Macrobenchmark. */
internal const val ITERATIONS = 5

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

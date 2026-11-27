package com.dannx.walls.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.staticCompositionLocalOf

/**
 * Señal compartida para que tocar la pestaña activa de la barra pida volver
 * arriba: la pestaña que está en pantalla escucha el contador y la que no,
 * no hace nada.
 */
class TabScrollBus {
    var tick by mutableStateOf(0)
        private set

    fun requestScrollToTop() {
        tick++
    }
}

/** Instancia del bus, la misma para la barra y la pantalla activa. */
val LocalTabScroll = staticCompositionLocalOf { TabScrollBus() }

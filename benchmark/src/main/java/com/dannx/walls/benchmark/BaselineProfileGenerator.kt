package com.dannx.walls.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera un Baseline Profile midiendo la app de verdad. Cualquier cosa que se
 * ejecute dentro del bloque entra en el perfil; por eso arranca y recorre el
 * feed, que es el camino crítico.
 *
 * El archivo queda en la salida adicional de los tests instrumentados
 * (`benchmark/build/outputs/connected_android_test_additional_output/...`).
 * Correrlo contra la variante `benchmark` (minificada) produce nombres
 * ofuscados; para versionar el perfil hay que ejecutar
 * `:benchmark:connectedNonMinifiedReleaseAndroidTest`, que lo genera contra la
 * copia sin R8 y deja nombres legibles que AGP remapea en cada build.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Si es la primera instalación, salir de la bienvenida para que el
        // perfil cubra el feed y no solo las tres pantallas iniciales.
        skipOnboardingIfNeeded()
        device.waitForIdle()
        scrollFeed()
    }
}

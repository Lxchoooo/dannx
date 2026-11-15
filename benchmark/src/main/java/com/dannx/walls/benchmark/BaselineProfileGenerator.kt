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
 * Ojo: la variante `benchmark` está minificada, así que los nombres salen
 * ofuscados y solo valen para ese mapping exacto. Para versionar el perfil hay
 * que generarlo contra una variante sin R8.
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
        device.waitForIdle()
        scrollFeed()
    }
}

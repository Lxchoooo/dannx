package com.dannx.walls.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cuando el modo lite está activo, las pantallas usan movimientos cortos o los
 * de Android por defecto, sin animaciones propias.
 */
val LocalLiteMode = staticCompositionLocalOf { false }

/**
 * Lite o animaciones desactivadas en el sistema (escala de animador en 0). Es
 * la señal que debe consultar todo movimiento nuevo antes de elegir un resorte
 * o una duración larga.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Duraciones base en milisegundos. El esquema expresivo usa tiempos algo más
 * largos que el estándar; acá se centralizan para que toda la app respire igual.
 */
object MotionDuration {
    const val Fast = 120
    const val Medium = 220
    const val Slow = 320
    const val ExtraSlow = 500
}

/** Curva enfatizada de Material 3 Expressive: arranca suave y frena largo. */
val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Curva estándar, para movimientos cortos que no deben llamar la atención. */
val StandardEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * Grupo de spec builders que respetan "reducir movimiento": en vez de devolver
 * otro ritmo, saltan al estado final. Así las pantallas no tienen que repetir
 * el `if (lite)` en cada animación.
 */
object Motion {

    /** Resorte sin rebote para desplazamientos de layout. */
    fun <T> spatial(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        }

    /** Resorte con rebote corto para confirmaciones (corazón, check, dock). */
    fun <T> bouncy(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
        }

    /** Resorte firme de presión: responde rápido y casi no rebota. */
    fun <T> press(reduceMotion: Boolean): FiniteAnimationSpec<T> =
        if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.6f, stiffness = 650f)
        }

    /** Desvanecido con la curva enfatizada; en reduce motion salta. */
    fun <T> fade(
        reduceMotion: Boolean,
        durationMillis: Int = MotionDuration.Medium,
        delayMillis: Int = 0,
        easing: Easing = EmphasizedEasing,
    ): FiniteAnimationSpec<T> =
        if (reduceMotion) {
            snap()
        } else {
            tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = easing,
            )
        }
}

/** Retraso por índice de la entrada escalonada de las grillas. */
const val StaggerStepMillis = 30L

/** Tope del retraso escalonado: nunca hace esperar más que esto. */
const val StaggerMaxMillis = 300L

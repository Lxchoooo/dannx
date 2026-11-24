package com.dannx.walls.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dannx.walls.ui.theme.Motion

/** Radio del difuminado que queda detrás de una hoja. */
private val BackdropRadius = 26.dp

/**
 * Alfa de la superficie de las hojas: opaca lo justo para que el texto se
 * lea y deja entrever el fondo difuminado, que es lo que la hace vidrio.
 */
const val GlassSheetAlpha = 0.88f

/**
 * Cuántas superficies de vidrio hay abiertas a la vez. Es un contador para
 * que dos encimadas no apaguen el difuminado a mitad del cierre.
 */
@Stable
class BackdropGlassState {
    var openCount by mutableIntStateOf(0)
        private set

    val isOpen: Boolean get() = openCount > 0

    internal fun acquire() {
        openCount += 1
    }

    internal fun release() {
        openCount -= 1
    }
}

/** Estado del vidrio compartido por toda la app, provisto en [AppRoot]. */
val LocalBackdropGlass = staticCompositionLocalOf { BackdropGlassState() }

/**
 * Envuelve una hoja o un menú: mientras esté compuesto, el contenido de la
 * app queda difuminado detrás. Es lo que hace que la superficie se lea como
 * vidrio y no como papel pegado encima. [enabled] permite apagarlo en
 * superficies que se componen siempre y solo se abren con un flag.
 */
@Composable
fun BackdropGlass(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val state = LocalBackdropGlass.current
    // El contenido no cambia de rama al abrir y cerrar: solo se adquiere o
    // se suelta el estado, así el menú no se vuelve a crear al desaparecer.
    val gated = if (enabled) state else null
    DisposableEffect(gated) {
        gated?.acquire()
        onDispose { gated?.release() }
    }
    content()
}

/**
 * Difumina el contenido mientras haya una hoja o un menú abierto. Se aplica
 * al contenedor y no a la hoja: la hoja vive en su propia ventana y no vería
 * lo que hay detrás. En versiones sin API 31 o en modo Lite no se aplica,
 * porque el efecto obliga a pintar el contenido en un búfer aparte.
 */
@Composable
fun Modifier.backdropGlass(
    state: BackdropGlassState,
    enabled: Boolean,
    reduceMotion: Boolean,
): Modifier {
    val active = enabled && state.isOpen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val radius = with(LocalDensity.current) { BackdropRadius.toPx() }
    val animated by animateFloatAsState(
        targetValue = if (active) radius else 0f,
        animationSpec = Motion.fade(reduceMotion),
        label = "backdropGlass",
    )
    // Con el radio en cero no se toca el efecto: sin él la capa no pasa a
    // dibujar aparte y el costo vuelve a ser el de siempre.
    val effect = remember(animated) {
        if (animated > 0.5f) BlurEffect(animated, animated) else null
    }
    return graphicsLayer { renderEffect = effect }
}

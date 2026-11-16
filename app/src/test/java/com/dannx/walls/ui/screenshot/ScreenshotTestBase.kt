package com.dannx.walls.ui.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dannx.walls.ui.theme.DannXTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Base de las capturas de Roborazzi: composables sueltos renderizados en JVM
 * con Robolectric Native Graphics. Los dorados quedan en
 * `app/src/test/screenshots` y se verifican con `verifyRoborazziDebug`.
 *
 * Fijo `dynamicColor = false` para que los colores salgan de las paletas
 * estáticas del app: el dynamic color de Robolectric no es representativo y
 * cambiaría entre versiones de Robolectric.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class ScreenshotTestBase {

    @get:Rule
    val composeRule = createComposeRule()

    protected fun capture(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            DannXTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier,
                    color = MaterialTheme.colorScheme.background,
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$name.png")
    }
}

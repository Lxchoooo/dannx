package com.dannx.walls.ui.editor

import android.graphics.ColorMatrix
import androidx.compose.runtime.Immutable
import kotlin.math.max

/** Escala máxima del editor. Es independiente del zoom de la ficha. */
internal const val EditorMaxZoom = 4f

/** Encuadre normalizado respecto del tamaño de la pantalla. */
@Immutable
data class EditorCrop(
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)

/** Ajustes de color, viñeta y grano. Todos van de -1 a 1 salvo los dos últimos. */
@Immutable
data class EditorAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
)

/** Un filtro rápido. La matriz se mezcla con la identidad según la intensidad. */
class FilterPreset(
    val id: String,
    val name: String,
    val values: FloatArray,
)

/** Todo lo que necesita el renderer para que la vista previa y el resultado final coincidan. */
@Immutable
data class EditorSettings(
    val crop: EditorCrop = EditorCrop(),
    val adjustments: EditorAdjustments = EditorAdjustments(),
    val filterId: String = "original",
    val filterIntensity: Float = 0f,
    val blur: Float = 0f,
)

/** Utilidades de encuadre y matrices de color compartidas por preview y render final. */
internal object EditorPresets {
    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun matrixArray(block: ColorMatrix.() -> Unit): FloatArray {
        val matrix = ColorMatrix()
        matrix.block()
        return FloatArray(20).also { matrix.getArray(it) }
    }

    private fun scaleMatrix(red: Float, green: Float, blue: Float): FloatArray =
        floatArrayOf(
            red, 0f, 0f, 0f, 0f,
            0f, green, 0f, 0f, 0f,
            0f, 0f, blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

    private fun contrastMatrix(contrast: Float): FloatArray {
        val offset = 127.5f * (1f - contrast)
        return floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun offsetMatrix(red: Float, green: Float, blue: Float): FloatArray =
        floatArrayOf(
            1f, 0f, 0f, 0f, red,
            0f, 1f, 0f, 0f, green,
            0f, 0f, 1f, 0f, blue,
            0f, 0f, 0f, 1f, 0f,
        )

    private val sepia = floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    val all: List<FilterPreset> = listOf(
        FilterPreset("original", "Original", identity.copyOf()),
        FilterPreset("calido", "Cálido", scaleMatrix(1.09f, 1.02f, 0.91f)),
        FilterPreset("frio", "Frío", scaleMatrix(0.91f, 1.01f, 1.10f)),
        FilterPreset(
            "mono",
            "Blanco y negro",
            matrixArray { setSaturation(0f) },
        ),
        FilterPreset("sepia", "Sepia", sepia.copyOf()),
        FilterPreset(
            "vintage",
            "Vintage",
            matrixArray {
                setSaturation(0.62f)
                postConcat(ColorMatrix(contrastMatrix(1.08f)))
                postConcat(ColorMatrix(scaleMatrix(1.05f, 0.98f, 0.88f)))
            },
        ),
        FilterPreset(
            "cine",
            "Cine",
            matrixArray {
                setSaturation(1.16f)
                postConcat(ColorMatrix(contrastMatrix(1.14f)))
                postConcat(ColorMatrix(scaleMatrix(0.97f, 1f, 1.08f)))
            },
        ),
        FilterPreset("verde", "Verde", scaleMatrix(0.93f, 1.07f, 0.96f)),
        FilterPreset("rosa", "Rosa", scaleMatrix(1.09f, 0.96f, 1.01f)),
        FilterPreset("contraste", "Contraste", contrastMatrix(1.22f)),
    )

    fun byId(id: String): FilterPreset =
        all.firstOrNull { it.id == id } ?: all.first()

    private fun blend(filter: FloatArray, intensity: Float): FloatArray {
        val amount = intensity.coerceIn(0f, 1f)
        return FloatArray(20) { index ->
            identity[index] + (filter[index] - identity[index]) * amount
        }
    }

    /**
     * Construye una sola matriz para todos los ajustes. El renderer la usa con
     * ColorMatrixColorFilter, así que mover un slider no vuelve a procesar la imagen.
     */
    fun colorMatrix(settings: EditorSettings): ColorMatrix {
        val output = ColorMatrix()
        val adjustments = settings.adjustments

        if (adjustments.saturation != 0f) {
            output.postConcat(
                ColorMatrix().apply {
                    setSaturation((1f + adjustments.saturation).coerceIn(0f, 2f))
                },
            )
        }
        if (adjustments.contrast != 0f) {
            val contrast = (1f + adjustments.contrast * 0.8f).coerceIn(0.2f, 1.8f)
            output.postConcat(ColorMatrix(contrastMatrix(contrast)))
        }
        if (adjustments.brightness != 0f) {
            val offset = adjustments.brightness * 70f
            output.postConcat(ColorMatrix(offsetMatrix(offset, offset, offset)))
        }
        if (adjustments.warmth != 0f) {
            val amount = adjustments.warmth * 0.12f
            output.postConcat(ColorMatrix(scaleMatrix(1f + amount, 1f, 1f - amount)))
        }
        if (adjustments.tint != 0f) {
            output.postConcat(
                ColorMatrix().apply {
                    setRotate(0, adjustments.tint * 120f)
                },
            )
        }

        val filter = byId(settings.filterId)
        if (filter.id != "original" && settings.filterIntensity > 0f) {
            output.postConcat(
                ColorMatrix(blend(filter.values, settings.filterIntensity)),
            )
        }
        return output
    }

    /**
     * Limita el desplazamiento para que la imagen nunca deje bordes vacíos.
     * El desplazamiento se guarda en unidades del ancho/alto del marco.
     */
    fun clampCrop(
        crop: EditorCrop,
        sourceWidth: Int,
        sourceHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
    ): EditorCrop {
        if (sourceWidth <= 0 || sourceHeight <= 0 || frameWidth <= 0f || frameHeight <= 0f) {
            return crop.copy(zoom = 1f, offsetX = 0f, offsetY = 0f)
        }
        val zoom = crop.zoom.coerceIn(1f, EditorMaxZoom)
        val cover = max(frameWidth / sourceWidth, frameHeight / sourceHeight)
        val displayWidth = sourceWidth * cover * zoom
        val displayHeight = sourceHeight * cover * zoom
        val maxX = ((displayWidth - frameWidth) / (2f * frameWidth)).coerceAtLeast(0f)
        val maxY = ((displayHeight - frameHeight) / (2f * frameHeight)).coerceAtLeast(0f)
        return crop.copy(
            zoom = zoom,
            offsetX = crop.offsetX.coerceIn(-maxX, maxX),
            offsetY = crop.offsetY.coerceIn(-maxY, maxY),
        )
    }
}

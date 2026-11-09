package com.dannx.walls.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorBlurTest {
    private fun solid(width: Int, height: Int, color: Int): IntArray =
        IntArray(width * height) { color }

    private fun noise(width: Int, height: Int): IntArray {
        var seed = 12345
        return IntArray(width * height) {
            seed = seed * 1103515245 + 12345
            val value = seed and 0x7FFFFFFF and 0xFF
            (0xFF shl 24) or (value shl 16) or ((0xFF - value) shl 8) or value
        }
    }

    /** Varianza del canal azul lejos de los bordes, donde no hay replicado. */
    private fun interiorVariance(pixels: IntArray, width: Int, height: Int, margin: Int): Double {
        var sum = 0.0
        var count = 0
        for (y in margin until height - margin) {
            for (x in margin until width - margin) {
                sum += (pixels[y * width + x] and 0xFF).toDouble()
                count++
            }
        }
        val mean = sum / count
        var variance = 0.0
        for (y in margin until height - margin) {
            for (x in margin until width - margin) {
                val delta = (pixels[y * width + x] and 0xFF) - mean
                variance += delta * delta
            }
        }
        return variance / count
    }

    @Test
    fun uniformImageStaysUniform() {
        val pixels = solid(16, 16, 0xFF3366CC.toInt())
        EditorBlur.boxBlur(pixels, 16, 16, radius = 5, passes = 3)
        assertTrue(pixels.all { it == 0xFF3366CC.toInt() })
    }

    @Test
    fun strongerRadiusReducesInteriorVariance() {
        val soft = noise(128, 128).also { EditorBlur.boxBlur(it, 128, 128, radius = 2) }
        val strong = noise(128, 128).also { EditorBlur.boxBlur(it, 128, 128, radius = 12) }
        assertTrue(interiorVariance(strong, 128, 128, 40) < interiorVariance(soft, 128, 128, 40))
    }

    @Test
    fun blurKeepsOpaqueAlpha() {
        val pixels = IntArray(32 * 32) { 0xFF000000.toInt() }
        pixels[0] = 0xFFFFFFFF.toInt()
        EditorBlur.boxBlur(pixels, 32, 32, radius = 4)
        assertTrue(pixels.all { (it ushr 24) == 0xFF })
    }

    @Test
    fun blurHandlesTinyImages() {
        val single = solid(1, 1, 0xFF102030.toInt())
        EditorBlur.boxBlur(single, 1, 1, radius = 9)
        assertEquals(0xFF102030.toInt(), single[0])

        val row = solid(8, 1, 0xFF445566.toInt())
        EditorBlur.boxBlur(row, 8, 1, radius = 3)
        assertTrue(row.all { it == 0xFF445566.toInt() })
    }

    @Test
    fun zeroBlurHasNoLayers() {
        assertNull(EditorBlur.mixFor(0f))
        assertNull(EditorBlur.mixFor(-0.5f))
    }

    @Test
    fun fullBlurUsesStrongestLevelOnly() {
        val mix = EditorBlur.mixFor(1f)
        assertEquals(EditorBlur.levelCount() - 1, mix?.baseLevel)
        assertNull(mix?.overlayLevel)
        assertEquals(0f, mix?.overlayAlpha ?: -1f, 0.0001f)
    }

    @Test
    fun middleIntensityCrossfadesAdjacentLevels() {
        // 0.5 × 3 niveles = 1.5: base nivel 0 y mezcla del nivel 1 al 50 %.
        val mix = EditorBlur.mixFor(0.5f)
        assertEquals(0, mix?.baseLevel)
        assertEquals(1, mix?.overlayLevel)
        assertEquals(0.5f, mix?.overlayAlpha ?: -1f, 0.0001f)
    }

    @Test
    fun firstLevelFadesOverTheSharpImage() {
        // Por debajo del primer escalón la base sigue siendo la imagen nítida.
        val mix = EditorBlur.mixFor(0.1f)
        assertNull(mix?.baseLevel)
        assertEquals(0, mix?.overlayLevel)
        assertEquals(0.3f, mix?.overlayAlpha ?: -1f, 0.0001f)
    }

    @Test
    fun radiiScaleWithSizeAndNeverCollapse() {
        assertArrayEqualsInt(intArrayOf(8, 22, 48), EditorBlur.radiiFor(480))
        assertArrayEqualsInt(intArrayOf(18, 50, 108), EditorBlur.radiiFor(1080))
        assertTrue(EditorBlur.radiiFor(1).all { it >= 1 })
    }

    @Test
    fun levelCountMatchesRadii() {
        assertEquals(EditorBlur.RadiusFractions.size, EditorBlur.levelCount())
        assertEquals(EditorBlur.levelCount(), EditorBlur.radiiFor(480).size)
    }

    private fun assertArrayEqualsInt(expected: IntArray, actual: IntArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}

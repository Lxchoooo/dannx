package com.dannx.walls

import com.dannx.walls.util.calculateInSampleSize
import com.dannx.walls.util.centerCropRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperGeometryTest {

    @Test
    fun `sample size stays one when the image matches the screen`() {
        assertEquals(1, calculateInSampleSize(1080, 1920, 1080, 1920))
    }

    @Test
    fun `sample size halves a big image until it fits`() {
        assertEquals(4, calculateInSampleSize(4320, 7680, 1080, 1920))
        assertEquals(2, calculateInSampleSize(2160, 3840, 1080, 1920))
    }

    @Test
    fun `sample size never goes below one with invalid input`() {
        assertEquals(1, calculateInSampleSize(0, 0, 1080, 1920))
        assertEquals(1, calculateInSampleSize(1080, 1920, 0, 0))
    }

    @Test
    fun `crop keeps the whole image when ratios match`() {
        val crop = centerCropRect(1080, 1920, 1080, 1920)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(1080, crop.width)
        assertEquals(1920, crop.height)
    }

    @Test
    fun `crop cuts the sides of a wide image`() {
        val crop = centerCropRect(3000, 1500, 1080, 1920)
        assertEquals(0, crop.top)
        assertEquals(1500, crop.height)
        assertTrue(crop.left > 0)
        assertTrue(crop.right < 3000)
        val ratio = crop.width.toDouble() / crop.height.toDouble()
        assertTrue(ratio > 0.55 && ratio < 0.58)
    }

    @Test
    fun `crop cuts the top and bottom of a tall image`() {
        val crop = centerCropRect(1080, 2400, 1080, 1920)
        assertEquals(0, crop.left)
        assertEquals(1080, crop.width)
        assertEquals(1920, crop.height)
        assertTrue(crop.top > 0)
        assertTrue(crop.bottom < 2400)
    }

    @Test
    fun `crop is centered`() {
        val crop = centerCropRect(3000, 1500, 1080, 1920)
        val leftGap = crop.left
        val rightGap = 3000 - crop.right
        assertTrue(leftGap - rightGap <= 1)
    }

    @Test
    fun `crop keeps the image when the target size is unknown`() {
        val crop = centerCropRect(1080, 1920, 0, 0)
        assertEquals(1080, crop.width)
        assertEquals(1920, crop.height)
    }
}

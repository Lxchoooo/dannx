package com.dannx.walls

import com.dannx.walls.util.DoubleTapZoom
import com.dannx.walls.util.MaxZoom
import com.dannx.walls.util.MinZoom
import com.dannx.walls.util.PanOffset
import com.dannx.walls.util.clampPan
import com.dannx.walls.util.clampScale
import com.dannx.walls.util.isDoubleTapDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomGeometryTest {

    @Test
    fun `scale is clamped between one and five`() {
        assertEquals(MinZoom, clampScale(0.4f))
        assertEquals(MaxZoom, clampScale(12f))
        assertEquals(2f, clampScale(2f))
    }

    @Test
    fun `pan is zero when the image is not zoomed`() {
        val pan = clampPan(PanOffset(200f, -300f), 1f, 1080f, 1920f)
        assertEquals(0f, pan.x)
        assertEquals(0f, pan.y)
    }

    @Test
    fun `pan is limited to the scaled image bounds`() {
        val pan = clampPan(PanOffset(9999f, 9999f), 3f, 1000f, 2000f)
        assertEquals(1000f, pan.x)
        assertEquals(2000f, pan.y)
    }

    @Test
    fun `pan keeps the image inside the view when dragged the other way`() {
        val pan = clampPan(PanOffset(-9999f, -9999f), 2f, 1000f, 2000f)
        assertEquals(-500f, pan.x)
        assertEquals(-1000f, pan.y)
    }

    @Test
    fun `double tap distance is detected inside the threshold`() {
        assertTrue(isDoubleTapDistance(40f))
        assertTrue(isDoubleTapDistance(-40f))
        assertTrue(!isDoubleTapDistance(400f))
    }

    @Test
    fun `double tap zoom is inside the allowed range`() {
        assertTrue(DoubleTapZoom > MinZoom)
        assertTrue(DoubleTapZoom < MaxZoom)
    }
}

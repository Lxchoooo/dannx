package com.dannx.walls.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `accepts a v prefix and compares numeric segments`() {
        val current = AppVersion.parse("1.5.0")
        val latest = AppVersion.parse("v1.6.0")

        assertEquals("1.6.0", latest?.raw)
        assertTrue(latest!!.isNewerThan(current!!))
        assertFalse(current.isNewerThan(latest))
    }

    @Test
    fun `compares each segment numerically instead of lexicographically`() {
        val current = AppVersion.parse("1.9.9")!!
        val latest = AppVersion.parse("1.10.0")!!

        assertTrue(latest.isNewerThan(current))
    }

    @Test
    fun `ignores prerelease and build suffixes`() {
        assertEquals(
            AppVersion.parse("1.6.0")!!.parts,
            AppVersion.parse("1.6.0-beta.1")!!.parts,
        )
        assertEquals(
            AppVersion.parse("1.6.0")!!.parts,
            AppVersion.parse("1.6.0+42")!!.parts,
        )
    }

    @Test
    fun `rejects empty or non numeric versions`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("v"))
        assertNull(AppVersion.parse("release"))
        assertNull(AppVersion.parse("1.x.0"))
    }
}

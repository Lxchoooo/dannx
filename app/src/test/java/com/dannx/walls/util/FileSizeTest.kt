package com.dannx.walls.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeTest {
    @Test
    fun formatsBytesWithTheRightUnit() {
        assertEquals("0 B", formatFileSize(0L, Locale.US))
        assertEquals("512 B", formatFileSize(512L, Locale.US))
        assertEquals("1023 B", formatFileSize(1023L, Locale.US))
        assertEquals("1 KB", formatFileSize(1024L, Locale.US))
        assertEquals("1.5 KB", formatFileSize(1536L, Locale.US))
        assertEquals("5 MB", formatFileSize(5L * 1024 * 1024, Locale.US))
        assertEquals("2.5 GB", formatFileSize((2.5 * 1024 * 1024 * 1024).toLong(), Locale.US))
    }

    @Test
    fun negativesShowZero() {
        assertEquals("0 B", formatFileSize(-10L, Locale.US))
    }
}

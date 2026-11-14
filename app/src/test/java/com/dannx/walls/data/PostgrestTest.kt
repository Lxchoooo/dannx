package com.dannx.walls.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PostgrestTest {
    @Test
    fun `wraps a single value in quotes`() {
        assertEquals("{\"cleopatra\"}", postgrestArray(listOf("cleopatra")))
    }

    @Test
    fun `separates several values`() {
        assertEquals(
            "{\"arte\",\"4k\"}",
            postgrestArray(listOf("arte", "4k")),
        )
    }

    @Test
    fun `keeps commas and braces inside quotes`() {
        assertEquals(
            "{\"día, noche\",\"a{b}\"}",
            postgrestArray(listOf("día, noche", "a{b}")),
        )
    }

    @Test
    fun `escapes quotes and backslashes`() {
        assertEquals(
            "{\"dice \\\"hola\\\"\",\"a\\\\b\"}",
            postgrestArray(listOf("dice \"hola\"", "a\\b")),
        )
    }

    @Test
    fun `an empty collection is an empty array`() {
        assertEquals("{}", postgrestArray(emptyList()))
    }
}

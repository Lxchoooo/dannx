package com.dannx.walls.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StringResolverTest {
    @Test
    fun getForwardsIdAndArguments() {
        val calls = mutableListOf<Pair<Int, List<Any>>>()
        val resolver = StringResolver { id, args ->
            calls += id to args.toList()
            "resolved"
        }

        assertEquals("resolved", resolver.get(42, "name", 7))
        assertEquals(42, calls.single().first)
        assertEquals(listOf<Any>("name", 7), calls.single().second)
    }

    @Test
    fun getWithoutArgumentsUsesEmptyArray() {
        var received: List<Any>? = null
        val resolver = StringResolver { _, args ->
            received = args.toList()
            "text"
        }

        assertEquals("text", resolver.get(1))
        assertEquals(emptyList<Any>(), received)
    }
}

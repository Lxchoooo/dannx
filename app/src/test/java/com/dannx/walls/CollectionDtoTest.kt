package com.dannx.walls

import com.dannx.walls.data.CollectionDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a collection without is_public is private`() {
        val dto = json.decodeFromString<CollectionDto>(
            """{"id":"c1","name":"Privada"}""",
        )

        assertFalse(dto.isPublic)
    }

    @Test
    fun `a public collection keeps is_public`() {
        val dto = json.decodeFromString<CollectionDto>(
            """{"id":"c2","name":"Pública","is_public":true}""",
        )

        assertTrue(dto.isPublic)
    }
}

package com.dannx.walls

import com.dannx.walls.data.CollectionDto
import com.dannx.walls.data.NewCollection
import com.dannx.walls.data.WallpaperCollection
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

    @Test
    fun `a new collection payload is private by default`() {
        val payload = NewCollection(name = "Privada", ownerName = "DannX")

        assertFalse(payload.isPublic)
    }

    @Test
    fun `a bare collection model is private by default`() {
        val collection = WallpaperCollection(id = "c3", name = "Privada")

        assertFalse(collection.isPublic)
    }
}

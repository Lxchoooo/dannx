package com.dannx.walls.data

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedCacheTest {
    private lateinit var folder: File

    @Before
    fun setUp() {
        folder = Files.createTempDirectory("dannx-feed-cache").toFile()
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun wallpapersAreCapped() = runTest {
        val cache = FeedCache(folder)
        val items = (0 until FeedCache.MAX_WALLPAPERS + 25).map { wallpaper("w$it") }

        cache.writeWallpapers(items)

        val restored = cache.readWallpapers()
        assertEquals(FeedCache.MAX_WALLPAPERS, restored.size)
        assertEquals("w0", restored.first().id)
        assertEquals("w${FeedCache.MAX_WALLPAPERS - 1}", restored.last().id)
    }

    @Test
    fun collectionsAreCapped() = runTest {
        val cache = FeedCache(folder)
        val items = (0 until FeedCache.MAX_COLLECTIONS + 10).map { collection("c$it") }

        cache.writeCollections(items)

        val restored = cache.readCollections()
        assertEquals(FeedCache.MAX_COLLECTIONS, restored.size)
        assertEquals("c0", restored.first().id)
        assertEquals("c${FeedCache.MAX_COLLECTIONS - 1}", restored.last().id)
    }

    @Test
    fun corruptCacheFallsBackToEmpty() = runTest {
        folder.resolve(FeedCache.WALLPAPERS_FILE).writeText("{not json")
        folder.resolve(FeedCache.COLLECTIONS_FILE).writeText("{not json")

        val cache = FeedCache(folder)

        assertTrue(cache.readWallpapers().isEmpty())
        assertTrue(cache.readCollections().isEmpty())
    }

    @Test
    fun sizeCountsFilesAndClearRemovesThem() = runTest {
        val cache = FeedCache(folder)
        cache.writeWallpapers(listOf(wallpaper("w1")))
        cache.writeCollections(listOf(collection("c1")))

        assertTrue(cache.sizeBytes() > 0L)

        cache.clear()

        assertEquals(0L, cache.sizeBytes())
        assertTrue(cache.readWallpapers().isEmpty())
        assertTrue(cache.readCollections().isEmpty())
    }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        title = id,
        category = "Test",
        imageUrl = "https://example.com/$id.jpg",
    )

    private fun collection(id: String) = WallpaperCollection(
        id = id,
        name = id,
    )
}

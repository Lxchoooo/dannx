package com.dannx.walls

import androidx.lifecycle.SavedStateHandle
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeRepository
    private lateinit var store: FakeStore
    private lateinit var cache: FakeCache
    private lateinit var actions: FakeActions

    private fun viewModel(online: Boolean = true): MainViewModel = MainViewModel(
        savedState = SavedStateHandle(),
        prefs = store,
        api = repository,
        cache = cache,
        actions = actions,
        isOnline = { online },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeRepository()
        store = FakeStore()
        cache = FakeCache()
        actions = FakeActions()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh loads the feed and fills the state`() = runTest(dispatcher) {
        repository.wallpapers = listOf(wallpaper("1"))
        val model = viewModel()

        model.refresh()
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.feed is LoadState.Content)
        assertEquals(1, state.wallpapers.size)
    }

    @Test
    fun `the app starts from the cache when there is no network`() = runTest(dispatcher) {
        cache.writeWallpapers(listOf(wallpaper("cached")))
        val model = viewModel(online = false)

        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state.feed is LoadState.Content)
        assertTrue(state.offline)
        assertEquals(1, state.wallpapers.size)
    }

    @Test
    fun `refresh cancels the previous call instead of stacking them`() = runTest(dispatcher) {
        repository.wallpapers = listOf(wallpaper("1"))
        val model = viewModel()

        model.refresh()
        model.refresh()
        advanceUntilIdle()

        assertEquals(1, model.state.value.wallpapers.size)
    }

    @Test
    fun `a transient failure keeps the cached feed and marks the app as offline`() =
        runTest(dispatcher) {
            repository.wallpapers = listOf(wallpaper("1"))
            val model = viewModel()
            model.refresh()
            advanceUntilIdle()

            repository.failWith = DannXError.Offline("sin red")
            repository.failTimes = 5
            model.refresh()
            advanceUntilIdle()

            val state = model.state.value
            assertTrue(state.feed is LoadState.Content)
            assertTrue(state.offline)
            assertEquals(1, state.wallpapers.size)
        }

    @Test
    fun `a failure without cache shows the error state with a message`() = runTest(dispatcher) {
        repository.failWith = DannXError.Offline("sin red")
        repository.failTimes = 5
        val model = viewModel()

        model.refresh()
        advanceUntilIdle()

        val feed = model.state.value.feed
        assertTrue(feed is LoadState.Error)
        assertEquals(ErrorKind.OFFLINE, (feed as LoadState.Error).kind)
        assertTrue(feed.message.contains("conexión"))
    }

    @Test
    fun `an empty feed is reported as empty and not as content`() = runTest(dispatcher) {
        repository.wallpapers = emptyList()
        val model = viewModel()

        model.refresh()
        advanceUntilIdle()

        assertTrue(model.state.value.feed is LoadState.Empty)
    }

    @Test
    fun `refreshIfOnline does nothing when there is no network`() = runTest(dispatcher) {
        repository.wallpapers = listOf(wallpaper("1"))
        val model = viewModel(online = false)

        model.refreshIfOnline()
        advanceUntilIdle()

        assertEquals(0, repository.loadCalls)
    }

    @Test
    fun `refreshIfOnline refreshes once when the network is back`() = runTest(dispatcher) {
        repository.wallpapers = listOf(wallpaper("1"))
        val model = viewModel(online = true)
        advanceUntilIdle()
        val before = repository.loadCalls

        model.refreshIfOnline()
        advanceUntilIdle()

        assertEquals(before + 1, repository.loadCalls)
    }

    @Test
    fun `download saves the file and remembers it`() = runTest(dispatcher) {
        val model = viewModel()

        model.download(wallpaper("1"))
        advanceUntilIdle()

        assertTrue(store.downloadedIds().contains("1"))
    }

    @Test
    fun `downloading twice asks the user before saving another copy`() = runTest(dispatcher) {
        val model = viewModel()
        model.download(wallpaper("1"))
        advanceUntilIdle()

        model.download(wallpaper("1"))
        advanceUntilIdle()

        assertTrue(model.state.value.downloadPrompt != null)
    }

    @Test
    fun `applying the wallpaper uses the chosen target`() = runTest(dispatcher) {
        val model = viewModel()

        model.setWallpaper(wallpaper("1"), com.dannx.walls.util.WallpaperTarget.LOCK)
        advanceUntilIdle()

        assertEquals(com.dannx.walls.util.WallpaperTarget.LOCK, actions.appliedTarget)
    }

    @Test
    fun `filters are kept in the saved state so they survive process death`() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val model = MainViewModel(
            savedState = saved,
            prefs = store,
            api = repository,
            cache = cache,
            actions = actions,
        )

        model.setQuery("noche")
        model.setCategory("Ciudad")
        model.setTag("luces")

        assertEquals("noche", saved.get<String>("query"))
        assertEquals("Ciudad", saved.get<String>("category"))
        assertEquals("luces", saved.get<String>("tag"))
    }

    @Test
    fun `a failed refresh keeps the current feed when the cache is empty`() =
        runTest(dispatcher) {
            repository.wallpapers = listOf(wallpaper("1"))
            val model = viewModel()
            advanceUntilIdle()

            cache.writeWallpapers(emptyList())
            repository.failWith = DannXError.Offline("sin red")
            repository.failTimes = 5
            model.refresh()
            advanceUntilIdle()

            val state = model.state.value
            assertTrue(state.feed is LoadState.Content)
            assertEquals("1", state.wallpapers.single().id)
            assertTrue(state.offline)
        }

    @Test
    fun `an expired session hides private collections but keeps the feed`() =
        runTest(dispatcher) {
            store.saveSession("admin@test", "token", null, "u1")
            repository.session = AdminSession("admin@test", "token", "u1")
            repository.wallpapers = listOf(wallpaper("1"))
            repository.collections = listOf(
                WallpaperCollection(id = "private", name = "Privada", isPublic = false),
                WallpaperCollection(id = "public", name = "Pública", isPublic = true),
            )
            val model = viewModel()
            advanceUntilIdle()

            assertEquals(2, model.state.value.collectionItems.size)

            repository.failWith = DannXError.SessionExpired
            repository.failTimes = 5
            model.refresh()
            advanceUntilIdle()

            val state = model.state.value
            assertEquals(null, state.session)
            assertTrue(state.collectionItems.none { !it.isPublic })
            assertTrue(state.feed is LoadState.Content)
            assertEquals("1", state.wallpapers.single().id)
        }

    @Test
    fun `sign out removes private collections from the state and the cache`() =
        runTest(dispatcher) {
            store.saveSession("admin@test", "token", null, "u1")
            repository.session = AdminSession("admin@test", "token", "u1")
            repository.collections = listOf(
                WallpaperCollection(id = "private", name = "Privada", isPublic = false),
                WallpaperCollection(id = "public", name = "Pública", isPublic = true),
            )
            val model = viewModel()
            advanceUntilIdle()
            assertEquals(2, model.state.value.collectionItems.size)

            repository.collections = listOf(
                WallpaperCollection(id = "public", name = "Pública", isPublic = true),
            )
            model.signOut()
            advanceUntilIdle()

            val state = model.state.value
            assertEquals(null, state.session)
            assertEquals(listOf("public"), state.collectionItems.map { it.id })
            assertEquals(listOf("public"), cache.readCollections().map { it.id })
        }

    @Test
    fun `an archived wallpaper can be edited from the studio`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.wallpapers = listOf(wallpaper("visible"))
        repository.adminWallpapers = listOf(
            wallpaper("archived").copy(archivedAt = "2026-01-01"),
        )
        val model = viewModel()
        advanceUntilIdle()

        model.loadStudio()
        advanceUntilIdle()
        assertTrue(model.state.value.adminWallpapers.any { it.id == "archived" })

        model.updateWallpaper(
            wallpaperId = "archived",
            title = "Título nuevo",
            category = "Abstracto",
            categoryId = null,
            tags = emptyList(),
            featured = false,
        )
        advanceUntilIdle()

        val updated = model.state.value.adminWallpapers.first { it.id == "archived" }
        assertEquals("Título nuevo", updated.title)
        assertEquals("Fondo actualizado.", model.state.value.message)
    }

    @Test
    fun `reserved category names are rejected before calling the server`() =
        runTest(dispatcher) {
            val model = viewModel()
            advanceUntilIdle()

            model.createCategory("Todos")
            advanceUntilIdle()

            assertTrue(model.state.value.message.orEmpty().contains("reservado"))
        }

    @Test
    fun `collection walls are replaced in one bulk operation`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.collections = listOf(
            WallpaperCollection(
                id = "c1",
                name = "Colección",
                ownerId = "u1",
                wallpaperIds = listOf("1", "2"),
            ),
        )
        val model = viewModel()
        advanceUntilIdle()

        model.setCollectionWallpapers("c1", listOf("2", "3"))
        advanceUntilIdle()

        assertEquals("c1" to listOf("2", "3"), repository.lastCollectionWallpapers)
        assertEquals(
            listOf("2", "3"),
            model.state.value.collectionItems.first { it.id == "c1" }.wallpaperIds,
        )
    }

    @Test
    fun `deleting a category reassigns its wallpapers in bulk`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.categories = listOf(
            com.dannx.walls.data.WallpaperCategory(id = "old", name = "Vieja", position = 0),
            com.dannx.walls.data.WallpaperCategory(id = "new", name = "Nueva", position = 1),
        )
        repository.adminWallpapers = listOf(
            wallpaper("1").copy(categoryId = "old", category = "Vieja"),
        )
        val model = viewModel()
        advanceUntilIdle()
        model.loadStudio()
        advanceUntilIdle()

        model.deleteCategory("old", "new")
        advanceUntilIdle()

        assertEquals(Triple("old", "new", "Nueva"), repository.deletedCategory)
        assertEquals("Nueva", model.state.value.adminWallpapers.single().category)
    }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        title = "Fondo $id",
        category = "Abstracto",
        imageUrl = "https://example.com/$id.jpg",
    )
}

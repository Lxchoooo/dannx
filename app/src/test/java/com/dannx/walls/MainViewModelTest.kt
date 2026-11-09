package com.dannx.walls

import androidx.lifecycle.SavedStateHandle
import com.dannx.walls.data.AdminSession
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.Wallpaper
import com.dannx.walls.data.WallpaperCollection
import com.dannx.walls.data.isLocal
import com.dannx.walls.util.StringResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val strings = StringResolver { id, _ ->
        when (id) {
            R.string.error_offline -> "Sin conexión. Revisá tu internet y probá de nuevo."
            R.string.msg_name_reserved -> "Ese nombre está reservado. Elegí otro."
            R.string.msg_wallpaper_updated -> "Fondo actualizado."
            R.string.msg_wallpapers_updated -> "Fondos actualizados."
            R.string.msg_category_delete_blocked ->
                "La categoría tiene fondos. Reasignalos antes de borrarla."
            else -> "string"
        }
    }

    private fun viewModel(
        online: Boolean = true,
        now: () -> Long = System::currentTimeMillis,
    ): MainViewModel = MainViewModel(
        savedState = SavedStateHandle(),
        prefs = store,
        api = repository,
        cache = cache,
        actions = actions,
        isOnline = { online },
        strings = strings,
        now = now,
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
    fun `the first page has 30 items and load more appends the next page`() =
        runTest(dispatcher) {
            repository.wallpapers = (1..45).map { wallpaper("$it") }
            val model = viewModel()
            advanceUntilIdle()

            assertEquals(30, model.state.value.wallpapers.size)
            assertTrue(model.state.value.hasMore)

            model.loadMore()
            advanceUntilIdle()

            val state = model.state.value
            assertEquals(45, state.wallpapers.size)
            assertEquals(45, state.wallpapers.map { it.id }.distinct().size)
            assertFalse(state.hasMore)
            assertEquals(30, repository.pageFilters.last().offset)
        }

    @Test
    fun `load more keeps the current feed when the request fails`() = runTest(dispatcher) {
        repository.wallpapers = (1..45).map { wallpaper("$it") }
        val model = viewModel()
        advanceUntilIdle()

        repository.failWith = DannXError.Offline("sin red")
        repository.failTimes = 5
        model.loadMore()
        advanceUntilIdle()

        val state = model.state.value
        assertEquals(30, state.wallpapers.size)
        assertTrue(state.loadMoreError.orEmpty().contains("conexión"))
        assertFalse(state.loadingMore)
    }

    @Test
    fun `changing a category resets the page and sends the filter to the api`() =
        runTest(dispatcher) {
            repository.wallpapers = listOf(
                wallpaper("1").copy(category = "Ciudad"),
                wallpaper("2").copy(category = "Abstracto"),
            )
            val model = viewModel()
            advanceUntilIdle()

            model.setCategory("Ciudad")
            advanceUntilIdle()

            val last = repository.pageFilters.last()
            assertEquals(0, last.offset)
            assertEquals("Ciudad", last.category)
            assertEquals(listOf("1"), model.state.value.wallpapers.map { it.id })
        }

    @Test
    fun `the cache merges fresh pages and keeps old downloads`() = runTest(dispatcher) {
        cache.writeWallpapers(listOf(wallpaper("old")))
        repository.wallpapers = (1..40).map { wallpaper("$it") }
        val model = viewModel()
        advanceUntilIdle()

        model.loadMore()
        advanceUntilIdle()

        val cachedIds = cache.readWallpapers().map { it.id }
        assertEquals(41, cachedIds.size)
        assertTrue(cachedIds.contains("old"))
        assertTrue(cachedIds.contains("40"))
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
    fun `refreshIfOnline waits five minutes before asking again`() = runTest(dispatcher) {
        repository.wallpapers = listOf(wallpaper("1"))
        var clock = 1_000_000L
        val model = viewModel(now = { clock })
        advanceUntilIdle()
        val before = repository.loadCalls

        clock += 60_000L
        model.refreshIfOnline()
        advanceUntilIdle()
        assertEquals(before, repository.loadCalls)

        clock += 5 * 60 * 1000L
        model.refreshIfOnline()
        advanceUntilIdle()
        assertEquals(before + 1, repository.loadCalls)
    }

    @Test
    fun `refreshIfOnline keeps the pages already loaded`() = runTest(dispatcher) {
        repository.wallpapers = (1..40).map { wallpaper("$it") }
        var clock = 1_000_000L
        val model = viewModel(now = { clock })
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(40, model.state.value.wallpapers.size)

        clock += 6 * 60 * 1000L
        model.refreshIfOnline()
        advanceUntilIdle()

        assertEquals(40, model.state.value.wallpapers.size)
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
    fun `recent searches keep the latest query first and ignore case duplicates`() =
        runTest(dispatcher) {
            val model = viewModel()

            model.setQuery("  minimal  ")
            model.rememberCurrentSearch()
            model.setQuery("dark")
            model.rememberCurrentSearch()
            model.setQuery("MINIMAL")
            model.rememberCurrentSearch()

            assertEquals(
                listOf("MINIMAL", "dark"),
                model.state.value.recentSearches,
            )

            model.clearRecentSearches()
            assertTrue(model.state.value.recentSearches.isEmpty())
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
            author = "Nueva autora",
            tags = emptyList(),
            featured = false,
        )
        advanceUntilIdle()

        val updated = model.state.value.adminWallpapers.first { it.id == "archived" }
        assertEquals("Título nuevo", updated.title)
        assertEquals("Nueva autora", updated.author)
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
    fun `without a session a collection is created only on the device`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()

        model.createCollection("Local", "Solo acá", isPublic = true)
        advanceUntilIdle()

        val collection = model.state.value.collectionItems.single()
        assertTrue(collection.isLocal)
        assertFalse(collection.isPublic)
        assertEquals(listOf(collection.id), store.localCollections().map { it.id })
    }

    @Test
    fun `refresh merges local collections before the server ones`() = runTest(dispatcher) {
        val local = WallpaperCollection(
            id = "local-1",
            name = "Local",
            ownerId = "local",
            wallpaperIds = listOf("1"),
        )
        store.saveLocalCollections(listOf(local))
        repository.wallpapers = listOf(wallpaper("1"))
        repository.collections = listOf(
            WallpaperCollection(id = "remote-1", name = "Remota", isPublic = true),
        )
        val model = viewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("local-1", "remote-1"),
            model.state.value.collectionItems.map { it.id },
        )
    }

    @Test
    fun `a missing wallpaper is removed from a local collection without an error`() =
        runTest(dispatcher) {
            val local = WallpaperCollection(
                id = "local-1",
                name = "Local",
                ownerId = "local",
                wallpaperIds = listOf("keep", "gone"),
            )
            store.saveLocalCollections(listOf(local))
            repository.wallpapers = listOf(wallpaper("keep"))
            val model = viewModel()
            advanceUntilIdle()

            val collection = model.state.value.collectionItems.single()
            assertEquals(listOf("keep"), collection.wallpaperIds)
            assertNull(model.state.value.message)
            assertEquals(listOf("keep"), store.localCollections().single().wallpaperIds)
        }

    @Test
    fun `moving a category updates its position and order`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.categories = listOf(
            com.dannx.walls.data.WallpaperCategory(id = "a", name = "A", position = 0),
            com.dannx.walls.data.WallpaperCategory(id = "b", name = "B", position = 1),
        )
        val model = viewModel()
        advanceUntilIdle()
        model.loadStudio()
        advanceUntilIdle()

        model.moveCategory("a", 1)
        advanceUntilIdle()

        val categories = model.state.value.categories
        assertEquals(listOf("b", "a"), categories.map { it.id })
        assertEquals(listOf(0, 1), categories.map { it.position })
    }

    @Test
    fun `saving app content updates the state and the local store`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        val model = viewModel()
        advanceUntilIdle()

        model.saveAppSettings("Frase nueva", "Aviso nuevo", true)
        advanceUntilIdle()

        assertEquals("Frase nueva", model.state.value.appSettings.tagline)
        assertEquals("Aviso nuevo", model.state.value.appSettings.bannerText)
        assertTrue(model.state.value.appSettings.bannerEnabled)
        assertEquals("Aviso nuevo", store.appSettings().bannerText)
    }

    @Test
    fun `bulk actions update several wallpapers at once`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.wallpapers = listOf(wallpaper("1"), wallpaper("2"))
        val model = viewModel()
        advanceUntilIdle()
        model.loadStudio()
        advanceUntilIdle()

        model.bulkSetFeatured(setOf("1", "2"), true)
        advanceUntilIdle()

        assertTrue(model.state.value.adminWallpapers.all { it.featured })
        assertEquals("Fondos actualizados.", model.state.value.message)
    }

    @Test
    fun `deleting a wallpaper moves it to the trash and restore brings it back`() =
        runTest(dispatcher) {
            store.saveSession("admin@test", "token", null, "u1")
            repository.session = AdminSession("admin@test", "token", "u1")
            repository.wallpapers = listOf(wallpaper("1"))
            val model = viewModel()
            advanceUntilIdle()
            model.loadStudio()
            advanceUntilIdle()

            model.deleteWallpaper("1")
            advanceUntilIdle()

            assertTrue(model.state.value.wallpapers.isEmpty())
            assertEquals(listOf("1"), model.state.value.trashWallpapers.map { it.id })
            assertTrue(repository.wallpapers.single { it.id == "1" }.isDeleted)

            model.restoreWallpapers(setOf("1"))
            advanceUntilIdle()

            assertTrue(model.state.value.trashWallpapers.isEmpty())
            assertEquals(listOf("1"), model.state.value.wallpapers.map { it.id })
            assertTrue(!repository.wallpapers.single { it.id == "1" }.isDeleted)
        }

    @Test
    fun `emptying the trash purges only deleted wallpapers`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.wallpapers = listOf(wallpaper("1"), wallpaper("2"))
        val model = viewModel()
        advanceUntilIdle()
        model.loadStudio()
        advanceUntilIdle()

        model.deleteWallpaper("1")
        advanceUntilIdle()
        model.emptyTrash()
        advanceUntilIdle()

        assertTrue(model.state.value.trashWallpapers.isEmpty())
        assertEquals(listOf("2"), model.state.value.wallpapers.map { it.id })
        assertEquals(listOf("2"), repository.wallpapers.map { it.id })
    }

    @Test
    fun `bulk archive and category keep the local state in sync`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.wallpapers = listOf(wallpaper("1"), wallpaper("2"))
        repository.categories = listOf(
            com.dannx.walls.data.WallpaperCategory(id = "cat", name = "Cat", position = 0),
        )
        val model = viewModel()
        advanceUntilIdle()
        model.loadStudio()
        advanceUntilIdle()

        model.bulkSetArchived(setOf("1"), true)
        advanceUntilIdle()
        model.bulkSetCategory(setOf("2"), "cat")
        advanceUntilIdle()

        val byId = model.state.value.adminWallpapers.associateBy { it.id }
        assertTrue(byId.getValue("1").isArchived)
        assertEquals("cat", byId.getValue("2").categoryId)
        assertEquals("Cat", byId.getValue("2").category)
    }

    @Test
    fun `featured order is saved from the studio`() = runTest(dispatcher) {
        store.saveSession("admin@test", "token", null, "u1")
        repository.session = AdminSession("admin@test", "token", "u1")
        repository.adminWallpapers = listOf(
            wallpaper("a").copy(featured = true, featuredPosition = 0),
            wallpaper("b").copy(featured = true, featuredPosition = 1),
        )
        val model = viewModel()
        advanceUntilIdle()
        model.loadStudio()
        advanceUntilIdle()

        model.moveFeatured("a", 1)
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), repository.featuredOrder)
        assertEquals(
            listOf("b", "a"),
            model.state.value.adminWallpapers
                .filter { it.featured }
                .sortedBy { it.featuredPosition }
                .map { it.id },
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

    @Test
    fun `deleting a category with wallpapers and no target shows a message and skips the api`() =
        runTest(dispatcher) {
            store.saveSession("admin@test", "token", null, "u1")
            repository.session = AdminSession("admin@test", "token", "u1")
            repository.categories = listOf(
                com.dannx.walls.data.WallpaperCategory(id = "old", name = "Vieja", position = 0),
            )
            repository.adminWallpapers = listOf(
                wallpaper("1").copy(categoryId = "old", category = "Vieja"),
            )
            val model = viewModel()
            advanceUntilIdle()
            model.loadStudio()
            advanceUntilIdle()

            model.deleteCategory("old")
            advanceUntilIdle()

            assertNull(repository.deletedCategory)
            assertTrue(model.state.value.message.orEmpty().contains("Reasignalos"))
        }

    @Test
    fun `detail loads a wallpaper by id when it is not in the feed`() = runTest(dispatcher) {
        repository.wallpapers = listOf(wallpaper("remote"))
        val model = viewModel(online = false)
        advanceUntilIdle()

        model.loadWallpaperDetail("remote")
        advanceUntilIdle()

        val detail = model.state.value.detailWallpaper as LoadState.Content<Wallpaper>
        assertEquals("remote", detail.data.id)
    }

    @Test
    fun `detail uses the cache before asking the repository`() = runTest(dispatcher) {
        val model = viewModel(online = false)
        advanceUntilIdle()
        cache.writeWallpapers(listOf(wallpaper("cached")))
        repository.failWith = DannXError.Offline("sin red")
        repository.failTimes = 5

        model.loadWallpaperDetail("cached")
        advanceUntilIdle()

        val detail = model.state.value.detailWallpaper as LoadState.Content<Wallpaper>
        assertEquals("cached", detail.data.id)
    }

    @Test
    fun `detail keeps an error with retry when the wallpaper cannot be loaded`() =
        runTest(dispatcher) {
            repository.failWith = DannXError.Offline("sin red")
            repository.failTimes = 5
            val model = viewModel(online = false)
            advanceUntilIdle()

            model.loadWallpaperDetail("missing")
            advanceUntilIdle()

            val detail = model.state.value.detailWallpaper
            assertTrue(detail is LoadState.Error)
            assertEquals(ErrorKind.OFFLINE, (detail as LoadState.Error).kind)
        }

    @Test
    fun `collection detail resolves the collection and its wallpapers by id`() =
        runTest(dispatcher) {
            repository.wallpapers = listOf(wallpaper("1"), wallpaper("2"))
            repository.collections = listOf(
                WallpaperCollection(
                    id = "c1",
                    name = "Colección",
                    ownerId = "u1",
                    wallpaperIds = listOf("1", "2"),
                ),
            )
            val model = viewModel(online = false)
            advanceUntilIdle()

            model.loadCollectionDetail("c1")
            advanceUntilIdle()

            val detail = model.state.value.detailCollection as LoadState.Content<CollectionDetailData>
            assertEquals("c1", detail.data.collection.id)
            assertEquals(listOf("1", "2"), detail.data.wallpapers.map { it.id })
        }

    @Test
    fun `collection detail shows an error instead of closing when it is missing`() =
        runTest(dispatcher) {
            val model = viewModel(online = false)
            advanceUntilIdle()

            model.loadCollectionDetail("missing")
            advanceUntilIdle()

            assertTrue(model.state.value.detailCollection is LoadState.Error)
        }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        title = "Fondo $id",
        category = "Abstracto",
        imageUrl = "https://example.com/$id.jpg",
    )
}

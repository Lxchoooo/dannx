package com.dannx.walls

import androidx.lifecycle.SavedStateHandle
import com.dannx.walls.data.DannXError
import com.dannx.walls.data.Wallpaper
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

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        title = "Fondo $id",
        category = "Abstracto",
        imageUrl = "https://example.com/$id.jpg",
    )
}

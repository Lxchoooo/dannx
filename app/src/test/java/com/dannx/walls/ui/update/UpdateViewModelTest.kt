package com.dannx.walls.ui.update

import com.dannx.walls.data.update.CachedUpdate
import com.dannx.walls.data.update.ReleaseInfo
import com.dannx.walls.data.update.UpdateCheckStore
import com.dannx.walls.data.update.UpdateRepository
import com.dannx.walls.util.StringResolver
import java.io.File
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
class UpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeUpdateRepository
    private lateinit var model: UpdateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeUpdateRepository()
        model = UpdateViewModel(
            repository = repository,
            strings = StringResolver { _, _ -> "mensaje" },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `offers a newer release`() = runTest(dispatcher) {
        model.check("1.5.0")
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state is UpdateState.Available)
        assertEquals("1.6.0", (state as UpdateState.Available).release.version)
    }

    @Test
    fun `reports when the current version is already the latest`() = runTest(dispatcher) {
        model.check("1.6.0")
        advanceUntilIdle()

        assertTrue(model.state.value is UpdateState.UpToDate)
    }

    @Test
    fun `downloads and exposes a ready file after progress`() = runTest(dispatcher) {
        model.check("1.5.0")
        advanceUntilIdle()

        model.download()
        advanceUntilIdle()

        val state = model.state.value
        assertTrue(state is UpdateState.Ready)
        val ready = state as UpdateState.Ready
        assertTrue(ready.file.exists())
        assertEquals(1, repository.progressCalls)
        ready.file.delete()
    }

    @Test
    fun `a failed check exposes a localized error`() = runTest(dispatcher) {
        repository.error = IllegalStateException("boom")
        model.check("1.5.0")
        advanceUntilIdle()

        assertEquals("mensaje", (model.state.value as UpdateState.Error).message)
    }

    @Test
    fun `background check only surfaces a newer release`() = runTest(dispatcher) {
        model.checkInBackground("1.5.0")
        advanceUntilIdle()

        assertTrue(model.state.value is UpdateState.Available)
    }

    @Test
    fun `background check stays silent on network errors`() = runTest(dispatcher) {
        repository.error = IllegalStateException("boom")
        model.checkInBackground("1.5.0")
        advanceUntilIdle()

        assertTrue(model.state.value is UpdateState.Idle)
    }

    @Test
    fun `reuses a fresh cached check without querying GitHub`() = runTest(dispatcher) {
        val store = FakeUpdateCheckStore()
        var now = 1_000_000L
        model = UpdateViewModel(
            repository = repository,
            strings = StringResolver { _, _ -> "mensaje" },
            checkStore = store,
            now = { now },
        )
        store.cached = CachedUpdate(repository.release, checkedAt = now)

        model.check("1.5.0")
        advanceUntilIdle()

        assertEquals(0, repository.latestCalls)
        assertTrue(model.state.value is UpdateState.Available)
    }

    @Test
    fun `force checks GitHub even if the cache is fresh`() = runTest(dispatcher) {
        val store = FakeUpdateCheckStore()
        var now = 1_000_000L
        model = UpdateViewModel(
            repository = repository,
            strings = StringResolver { _, _ -> "mensaje" },
            checkStore = store,
            now = { now },
        )
        store.cached = CachedUpdate(repository.release, checkedAt = now)

        model.check("1.5.0", force = true)
        advanceUntilIdle()

        assertEquals(1, repository.latestCalls)
        assertEquals(1, store.writes)
    }

    @Test
    fun `queries GitHub again after the cache expires`() = runTest(dispatcher) {
        val store = FakeUpdateCheckStore()
        var now = 1_000_000L
        model = UpdateViewModel(
            repository = repository,
            strings = StringResolver { _, _ -> "mensaje" },
            checkStore = store,
            now = { now },
        )
        store.cached = CachedUpdate(repository.release, checkedAt = now)
        now += 24L * 60L * 60L * 1000L

        model.check("1.5.0")
        advanceUntilIdle()

        assertEquals(1, repository.latestCalls)
        assertEquals(1, store.writes)
    }

    private class FakeUpdateCheckStore : UpdateCheckStore {
        var cached: CachedUpdate? = null
        var writes = 0

        override fun read(): CachedUpdate? = cached

        override fun write(release: ReleaseInfo, checkedAt: Long) {
            writes += 1
            cached = CachedUpdate(release = release, checkedAt = checkedAt)
        }
    }

    private class FakeUpdateRepository : UpdateRepository {
        var error: Throwable? = null
        var progressCalls = 0
        var latestCalls = 0
        val release = ReleaseInfo(
            version = "1.6.0",
            tagName = "v1.6.0",
            notes = "Changes",
            apkUrl = "https://example.com/DannX.apk",
            checksumUrl = "https://example.com/DannX.apk.sha256",
            htmlUrl = "https://example.com/release",
            publishedAt = "",
        )

        override suspend fun latestRelease(): ReleaseInfo {
            latestCalls += 1
            error?.let { throw it }
            return release
        }

        override suspend fun downloadVerified(
            release: ReleaseInfo,
            onProgress: (downloaded: Long, total: Long) -> Unit,
        ): File {
            progressCalls += 1
            onProgress(50, 100)
            return File.createTempFile("dannx-update", ".apk")
        }
    }
}

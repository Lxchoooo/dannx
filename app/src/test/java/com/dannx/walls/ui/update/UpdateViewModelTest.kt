package com.dannx.walls.ui.update

import com.dannx.walls.data.update.ReleaseInfo
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

    private class FakeUpdateRepository : UpdateRepository {
        var error: Throwable? = null
        var progressCalls = 0
        private val release = ReleaseInfo(
            version = "1.6.0",
            tagName = "v1.6.0",
            notes = "Changes",
            apkUrl = "https://example.com/DannX.apk",
            checksumUrl = "https://example.com/DannX.apk.sha256",
            htmlUrl = "https://example.com/release",
            publishedAt = "",
        )

        override suspend fun latestRelease(): ReleaseInfo {
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

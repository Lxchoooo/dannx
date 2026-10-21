package com.dannx.walls

import com.dannx.walls.data.DannXError
import com.dannx.walls.data.RetryPolicy
import com.dannx.walls.data.isTransient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `the wait grows exponentially between attempts`() {
        val policy = RetryPolicy(initialDelayMillis = 400)
        assertEquals(400, policy.delayFor(0))
        assertEquals(800, policy.delayFor(1))
        assertEquals(1600, policy.delayFor(2))
    }

    @Test
    fun `a transient failure is retried until it succeeds`() = runTest {
        val waits = mutableListOf<Long>()
        val policy = RetryPolicy(maxAttempts = 3, sleeper = { waits += it })

        val result = policy.run { attempt ->
            if (attempt < 2) throw DannXError.Offline("sin red")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(400L, 800L), waits)
    }

    @Test
    fun `a permanent failure is not retried`() = runTest {
        val policy = RetryPolicy(maxAttempts = 3)
        var attempts = 0

        val error = runCatching {
            policy.run { attempt ->
                attempts += 1
                throw DannXError.SessionExpired
            }
        }.exceptionOrNull()

        assertEquals(1, attempts)
        assertTrue(error is DannXError.SessionExpired)
    }

    @Test
    fun `the attempts are limited`() = runTest {
        val policy = RetryPolicy(maxAttempts = 3)
        var attempts = 0

        runCatching {
            policy.run { attempt ->
                attempts += 1
                throw DannXError.Offline("sin red")
            }
        }

        assertEquals(3, attempts)
    }

    @Test
    fun `only transient errors are retried`() {
        assertTrue(DannXError.Offline("sin red").isTransient())
        assertTrue(DannXError.Server(503).isTransient())
        assertFalse(DannXError.Server(404).isTransient())
        assertFalse(DannXError.SessionExpired.isTransient())
        assertFalse(DannXError.Unexpected("raro").isTransient())
    }
}

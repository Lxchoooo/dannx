package com.dannx.walls.data

import kotlinx.coroutines.delay

/**
 * Reintentos con espera exponencial para fallos transitorios.
 * La espera es inyectable para poder probarla sin dormir de verdad.
 */
class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMillis: Long = 400,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    /**
     * Ejecuta [block] reintentando solo cuando [shouldRetry] lo indica.
     */
    suspend fun <T> run(
        shouldRetry: (Throwable) -> Boolean = { it.isTransient() },
        block: suspend (attempt: Int) -> T,
    ): T {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < maxAttempts) {
            try {
                return block(attempt)
            } catch (error: Throwable) {
                lastError = error
                if (!shouldRetry(error) || attempt == maxAttempts - 1) {
                    throw error
                }
                sleeper(delayFor(attempt))
                attempt += 1
            }
        }
        throw lastError ?: IllegalStateException("Reintento sin error")
    }

    /**
     * Espera para el intento [attempt] (0 based): 400 ms, 800 ms, 1600 ms…
     */
    fun delayFor(attempt: Int): Long = initialDelayMillis shl attempt.coerceAtLeast(0)
}

/**
 * Un error de red es pasable si se puede reintentar más tarde.
 */
fun Throwable.isTransient(): Boolean = when (this) {
    is DannXError.Offline -> true
    is DannXError.Server -> code >= 500
    is DannXError.Parse -> true
    is java.io.IOException -> true
    else -> false
}

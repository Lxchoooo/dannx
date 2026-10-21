package com.dannx.walls.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Errores de red y de sesión con un tipo propio, para que la interfaz pueda
 * mostrar mensajes claros en vez de texto crudo del servidor.
 */
sealed class DannXError(message: String) : IOException(message) {
    /** No hay conexión o el servidor no responde. */
    data class Offline(val causeDetail: String) : DannXError(causeDetail)

    /** El servidor respondió con un error. */
    data class Server(val code: Int) : DannXError("El servidor respondió con el código $code.")

    /** La sesión venció y no se pudo renovar. */
    data object SessionExpired : DannXError("Tu sesión venció.")

    /** Fallo al analizar la respuesta. */
    data class Parse(val causeDetail: String) : DannXError(causeDetail)

    /** Cualquier otro fallo. */
    data class Unexpected(val causeDetail: String) : DannXError(causeDetail)
}

/**
 * Convierte una excepción de red en un error tipado.
 */
fun Throwable.toDannXError(): DannXError = when (this) {
    is DannXError -> this
    is UnknownHostException,
    is ConnectException,
    is SocketTimeoutException,
    -> DannXError.Offline(message.orEmpty())

    is IOException -> DannXError.Offline(message.orEmpty())
    else -> DannXError.Unexpected(message ?: this::class.java.simpleName)
}

/**
 * Indica si el error es pasable: se puede reintentar más tarde.
 */
fun DannXError.isRetryable(): Boolean = when (this) {
    is DannXError.Offline -> true
    is DannXError.Server -> code >= 500
    is DannXError.Parse -> true
    is DannXError.SessionExpired -> false
    is DannXError.Unexpected -> false
}

package com.dannx.walls.data

import okhttp3.OkHttpClient
import okhttp3.Request

/** Respuesta HTTP ya leída, para decidir errores fuera del cliente. */
internal data class HttpResult(val code: Int, val body: String)

internal fun errorFor(code: Int, body: String): DannXError = when (code) {
    401 -> DannXError.SessionExpired
    403 -> DannXError.Forbidden
    in 500..599 -> DannXError.Server(code)
    else -> DannXError.Unexpected(
        "El servidor respondió $code${if (body.isBlank()) "" else ": ${body.take(120)}"}",
    )
}

internal fun OkHttpClient.runRequest(request: Request): HttpResult =
    newCall(request).execute().use { response ->
        HttpResult(response.code, response.body?.string().orEmpty())
    }

internal fun HttpResult.readBody(allowConflict: Boolean = false): String {
    if (code == 401) {
        throw DannXError.SessionExpired
    }
    if (code == 409 && allowConflict) {
        return body
    }
    if (code !in 200..299) {
        throw errorFor(code, body)
    }
    return body
}

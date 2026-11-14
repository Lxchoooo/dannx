package com.dannx.walls.util

/** Extensión de archivo que corresponde a un tipo de imagen. */
internal fun extensionForMime(mime: String): String = when (mime) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    else -> "jpg"
}

package com.dannx.walls.data

/**
 * Literal de arreglo para los filtros de PostgREST. Cada valor va entre
 * comillas para que las etiquetas con comas, llaves o comillas no rompan el
 * parámetro.
 */
internal fun postgrestArray(values: Collection<String>): String =
    values.joinToString(separator = ",", prefix = "{", postfix = "}") { value ->
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""
    }

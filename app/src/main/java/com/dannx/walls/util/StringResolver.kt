package com.dannx.walls.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * Acceso a cadenas localizadas desde capas que no son de Compose (por ejemplo,
 * ViewModels). En producción se respalda con `Context.getString`; en los tests
 * se puede reemplazar por una implementación que devuelva textos fijos.
 */
fun interface StringResolver {
    fun resolve(@StringRes id: Int, args: Array<out Any>): String
}

fun StringResolver.get(@StringRes id: Int, vararg args: Any): String = resolve(id, args)

fun Context.stringResolver(): StringResolver = StringResolver { id, args ->
    getString(id, *args)
}

/**
 * Ankit Khatri KC (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.extensions

fun <T> tryOrNull(block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        null
    }

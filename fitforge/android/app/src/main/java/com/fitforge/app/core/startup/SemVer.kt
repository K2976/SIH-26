package com.fitforge.app.core.startup

/**
 * True if [installed] is a lower dotted version than [minimum]
 * (e.g. "0.9.0" < "1.0.0"). Missing components compare as 0; any
 * pre-release suffix (`-beta1`) is ignored.
 */
fun isVersionBelow(installed: String, minimum: String): Boolean {
    val installedParts = installed.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
    val minimumParts = minimum.substringBefore('-').split(".").map { it.toIntOrNull() ?: 0 }
    val length = maxOf(installedParts.size, minimumParts.size)
    for (i in 0 until length) {
        val installedPart = installedParts.getOrElse(i) { 0 }
        val minimumPart = minimumParts.getOrElse(i) { 0 }
        if (installedPart != minimumPart) return installedPart < minimumPart
    }
    return false
}

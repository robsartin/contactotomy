package com.robsartin.contactotomy.core.normalize

object EmailNormalizer {
    /** Lowercases and trims; returns null if blank. */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().lowercase()
        return trimmed.ifEmpty { null }
    }
}

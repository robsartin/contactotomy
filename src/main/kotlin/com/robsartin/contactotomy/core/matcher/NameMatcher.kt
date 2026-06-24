package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.ContactName

/** Compares names for identity, allowing nicknames, initials, and dropped middles. */
class NameMatcher(
    private val nicknames: NicknameDictionary,
) {
    /** A positive given-name match reason, or null if there is no positive match. */
    fun givenMatch(
        a: ContactName,
        b: ContactName,
    ): MatchReason? {
        val x = norm(a.given)
        val y = norm(b.given)
        if (x.isEmpty() || y.isEmpty()) return null
        if (x == y) return MatchReason.NAME_EXACT
        if (nicknames.areEquivalent(x, y)) return MatchReason.NAME_NICKNAME
        if (initialCompatible(x, y)) return MatchReason.NAME_INITIAL
        return null
    }

    /** True only when both given names are present and clearly NOT the same person. */
    fun givenConflict(
        a: ContactName,
        b: ContactName,
    ): Boolean {
        val x = norm(a.given)
        val y = norm(b.given)
        if (x.isEmpty() || y.isEmpty()) return false
        return givenMatch(a, b) == null
    }

    /** Family names are compatible when equal, or when at least one is missing. */
    fun familyMatches(
        a: ContactName,
        b: ContactName,
    ): Boolean {
        val x = norm(a.family)
        val y = norm(b.family)
        if (x.isEmpty() || y.isEmpty()) return true
        return x == y
    }

    /** True when both family names are present and unequal (a possible surname change). */
    fun familyDiffers(
        a: ContactName,
        b: ContactName,
    ): Boolean {
        val x = norm(a.family)
        val y = norm(b.family)
        return x.isNotEmpty() && y.isNotEmpty() && x != y
    }

    private fun initialCompatible(
        x: String,
        y: String,
    ): Boolean {
        if (x.length == 1 && y.isNotEmpty()) return x[0] == y[0]
        if (y.length == 1 && x.isNotEmpty()) return y[0] == x[0]
        return false
    }

    private fun norm(s: String?): String =
        s
            ?.lowercase()
            ?.replace(".", "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim() ?: ""
}

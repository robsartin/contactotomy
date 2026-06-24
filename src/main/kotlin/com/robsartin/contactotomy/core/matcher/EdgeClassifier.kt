package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

/** Classifies a pair of contacts into a MatchEdge, or null when they must never merge. */
class EdgeClassifier(
    private val nameMatcher: NameMatcher,
) {
    fun classify(
        a: Contact,
        b: Contact,
    ): MatchEdge? {
        // Rule 1: clearly different given names => never merge, even with shared contact info.
        if (nameMatcher.givenConflict(a.name, b.name)) return null

        val sharedPhone = a.phones.any { it in b.phones }
        val sharedEmail = a.emails.any { it in b.emails }
        val hasSharedContact = sharedPhone || sharedEmail
        val givenReason = nameMatcher.givenMatch(a.name, b.name)

        val reasons = mutableListOf<MatchReason>()
        if (sharedPhone) reasons += MatchReason.SHARED_PHONE
        if (sharedEmail) reasons += MatchReason.SHARED_EMAIL

        // Rule 2: positive name match + shared contact info => HIGH (surname change flagged).
        if (hasSharedContact && givenReason != null) {
            reasons += givenReason
            if (nameMatcher.familyDiffers(a.name, b.name)) reasons += MatchReason.SURNAME_CHANGE
            return MatchEdge(a, b, Confidence.HIGH, reasons)
        }

        // Rule 3: name-only (given matches, family compatible) without shared contact => UNCERTAIN.
        if (!hasSharedContact && givenReason != null && nameMatcher.familyMatches(a.name, b.name)) {
            reasons += givenReason
            reasons += MatchReason.NAME_ONLY
            return MatchEdge(a, b, Confidence.UNCERTAIN, reasons)
        }

        // Rule 5: shared contact but indeterminate name (no positive match, no conflict) => UNCERTAIN.
        if (hasSharedContact && givenReason == null) {
            reasons += MatchReason.NAME_ONLY
            return MatchEdge(a, b, Confidence.UNCERTAIN, reasons)
        }

        return null
    }
}

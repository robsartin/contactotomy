package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

enum class Confidence { HIGH, UNCERTAIN }

enum class MatchReason {
    SHARED_PHONE,
    SHARED_EMAIL,
    NAME_EXACT,
    NAME_NICKNAME,
    NAME_DROPPED_MIDDLE,
    NAME_INITIAL,
    SURNAME_CHANGE,
    NAME_ONLY,
}

data class MatchEdge(
    val a: Contact,
    val b: Contact,
    val confidence: Confidence,
    val reasons: List<MatchReason>,
)

data class Cluster(
    val id: String,
    val members: List<Contact>,
    val confidence: Confidence,
    val reasons: List<MatchReason>,
)

data class MatchResult(
    val clusters: List<Cluster>,
    val uncertainPairs: List<MatchEdge>,
)

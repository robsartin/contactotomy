package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant

enum class TextField { EMAIL, NAME, ORG, ADDRESS, URL, NOTES }

enum class PredicateKind {
    NO_NAME_AND_NO_PHONE,
    NO_EMAIL,
    EMPTY_CARD,
    CREATED_BEFORE,
    SOURCE_IS,
    NEVER_CONTACTED,
}

sealed interface Condition

data class TextMatch(
    val field: TextField,
    val glob: String,
) : Condition

data class PhoneMatch(
    val pattern: String,
) : Condition

data class Predicate(
    val kind: PredicateKind,
    val before: Instant? = null,
    val source: Source? = null,
) : Condition

data class And(
    val of: List<Condition>,
) : Condition

data class Or(
    val of: List<Condition>,
) : Condition

data class Not(
    val of: Condition,
) : Condition

data class Rule(
    val name: String,
    val condition: Condition,
)

data class RuleSet(
    val rules: List<Rule>,
) {
    companion object
}

data class RuleMatch(
    val ruleName: String,
    val reason: String,
)

data class Flagged(
    val contact: Contact,
    val matches: List<RuleMatch>,
)

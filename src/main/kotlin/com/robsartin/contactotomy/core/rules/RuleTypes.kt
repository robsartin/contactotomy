package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Serializable
sealed interface Condition

@Serializable
@SerialName("text")
data class TextMatch(
    val field: TextField,
    val glob: String,
) : Condition

@Serializable
@SerialName("phone")
data class PhoneMatch(
    val pattern: String,
) : Condition

@Serializable
@SerialName("predicate")
data class Predicate(
    val kind: PredicateKind,
    @Serializable(with = InstantIso8601Serializer::class) val before: Instant? = null,
    val source: Source? = null,
) : Condition

@Serializable
@SerialName("and")
data class And(
    val of: List<Condition>,
) : Condition

@Serializable
@SerialName("or")
data class Or(
    val of: List<Condition>,
) : Condition

@Serializable
@SerialName("not")
data class Not(
    val of: Condition,
) : Condition

@Serializable
data class Rule(
    val name: String,
    val condition: Condition,
)

@Serializable
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

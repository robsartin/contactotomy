package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact

/** Evaluates a Condition against a Contact, returning a human reason when it matches. */
internal class ConditionEvaluator {
    /** Reason string if [condition] matches [contact], otherwise null. */
    fun matchReason(
        condition: Condition,
        contact: Contact,
    ): String? =
        when (condition) {
            is TextMatch ->
                if (textValues(condition.field, contact).any { Glob.matches(condition.glob, it) }) describe(condition) else null
            is PhoneMatch ->
                if (contact.phones.any { PhonePattern.matches(condition.pattern, it) }) describe(condition) else null
            is Predicate ->
                if (Predicates.evaluate(condition, contact)) describe(condition) else null
            is And -> {
                val parts = condition.of.map { matchReason(it, contact) }
                when {
                    condition.of.isEmpty() -> "(always)"
                    parts.all { it != null } -> parts.filterNotNull().joinToString(" AND ")
                    else -> null
                }
            }
            is Or -> {
                val parts = condition.of.mapNotNull { matchReason(it, contact) }
                if (parts.isNotEmpty()) parts.joinToString(" OR ") else null
            }
            is Not ->
                if (matchReason(condition.of, contact) == null) "NOT (${describe(condition.of)})" else null
        }

    /** Static rendering of a condition, independent of any contact. */
    fun describe(condition: Condition): String =
        when (condition) {
            is TextMatch -> "${condition.field.name.lowercase()} matches ${condition.glob}"
            is PhoneMatch -> "phone matches ${condition.pattern}"
            is Predicate -> describePredicate(condition)
            is And -> condition.of.joinToString(" AND ", "(", ")") { describe(it) }
            is Or -> condition.of.joinToString(" OR ", "(", ")") { describe(it) }
            is Not -> "NOT (${describe(condition.of)})"
        }

    private fun describePredicate(p: Predicate): String =
        when (p.kind) {
            PredicateKind.NO_NAME_AND_NO_PHONE -> "no name and no phone"
            PredicateKind.NO_PHONE -> "no phone"
            PredicateKind.NO_EMAIL -> "no email"
            PredicateKind.EMPTY_CARD -> "empty card"
            PredicateKind.CREATED_BEFORE -> "created before ${p.before}"
            PredicateKind.SOURCE_IS -> "source is ${p.source}"
            PredicateKind.NEVER_CONTACTED -> "never contacted"
        }

    private fun textValues(
        field: TextField,
        c: Contact,
    ): List<String> =
        when (field) {
            TextField.EMAIL -> c.emails
            TextField.NAME -> listOfNotNull(c.name.formatted, c.name.given, c.name.family)
            TextField.ORG -> listOfNotNull(c.org)
            TextField.ADDRESS -> c.addresses
            TextField.URL -> c.urls
            TextField.NOTES -> listOfNotNull(c.notes)
        }
}

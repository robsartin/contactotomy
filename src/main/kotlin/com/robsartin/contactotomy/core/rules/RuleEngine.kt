package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact

/** Evaluates a RuleSet over contacts, returning the flagged contacts with per-rule reasons. */
object RuleEngine {
    private val evaluator = ConditionEvaluator()

    fun evaluate(
        contacts: List<Contact>,
        ruleSet: RuleSet,
    ): List<Flagged> =
        contacts.mapNotNull { contact ->
            val matches =
                ruleSet.rules.mapNotNull { rule ->
                    evaluator.matchReason(rule.condition, contact)?.let { RuleMatch(rule.name, it) }
                }
            if (matches.isNotEmpty()) Flagged(contact, matches) else null
        }
}

/** Returns [contacts] with the approved ids removed, preserving order. */
fun applyDeletions(
    contacts: List<Contact>,
    approvedIds: Set<String>,
): List<Contact> = contacts.filterNot { it.id in approvedIds }

package com.robsartin.contactotomy.core.apply

import com.robsartin.contactotomy.core.merger.MergeProposal
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.core.normalize.PhoneNormalizer

/** Applies accept/reject/field decisions to produce the final deduplicated contact list. */
class DecisionApplier {
    private val phoneNormalizer = PhoneNormalizer()

    fun applyDecisions(
        allContacts: List<Contact>,
        proposals: List<MergeProposal>,
        decisions: List<MergeDecision>,
    ): List<Contact> {
        val decisionByCluster = decisions.associateBy { it.clusterId }
        val accepted =
            proposals.filter {
                (decisionByCluster[it.cluster.id]?.action ?: Action.REJECT) == Action.ACCEPT
            }

        val acceptedMemberIds = accepted.flatMap { it.cluster.members.map { m -> m.id } }.toSet()
        val indexOf = allContacts.withIndex().associate { (i, c) -> c.id to i }
        val mergedAtAnchor = HashMap<Int, Contact>()
        for (p in accepted) {
            val anchor = p.cluster.members.minOf { indexOf.getValue(it.id) }
            mergedAtAnchor[anchor] = adjust(p, decisionByCluster.getValue(p.cluster.id))
        }

        val result = ArrayList<Contact>()
        allContacts.forEachIndexed { i, c ->
            mergedAtAnchor[i]?.let { result.add(it) }
            if (c.id !in acceptedMemberIds) result.add(c)
        }
        return result
    }

    fun adjust(
        proposal: MergeProposal,
        decision: MergeDecision,
    ): Contact {
        var merged = proposal.merged
        for ((field, value) in decision.conflictChoices) merged = setSingle(merged, field, value)
        for (excluded in decision.excludedValues) merged = removeValue(merged, excluded.field, excluded.value)
        return merged
    }

    private fun setSingle(
        c: Contact,
        field: String,
        value: String,
    ): Contact =
        when (field) {
            "org" -> c.copy(org = value)
            "title" -> c.copy(title = value)
            "notes" -> c.copy(notes = value)
            else -> c
        }

    private fun removeValue(
        c: Contact,
        field: String,
        value: String,
    ): Contact =
        when (field) {
            // Drop the normalized phone and any rawPhones original that normalizes to it,
            // so an excluded number can't be resurrected from rawPhones on export.
            "phones" ->
                c.copy(
                    phones = c.phones - value,
                    rawPhones = c.rawPhones.filterNot { phoneNormalizer.normalize(it) == value },
                )
            "rawPhones" -> c.copy(rawPhones = c.rawPhones - value)
            "emails" -> c.copy(emails = c.emails - value)
            "addresses" -> c.copy(addresses = c.addresses.filterNot { it.toDisplayString() == value })
            "urls" -> c.copy(urls = c.urls - value)
            "categories" -> c.copy(categories = c.categories - value)
            "org" -> if (c.org == value) c.copy(org = null) else c
            "title" -> if (c.title == value) c.copy(title = null) else c
            "notes" -> if (c.notes == value) c.copy(notes = null) else c
            else -> c
        }
}

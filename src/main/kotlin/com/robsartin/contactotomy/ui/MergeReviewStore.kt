package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.Action
import com.robsartin.contactotomy.core.apply.DecisionApplier
import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.apply.MergeDecision
import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.ContactMatcher
import com.robsartin.contactotomy.core.matcher.EdgeClassifier
import com.robsartin.contactotomy.core.matcher.NameMatcher
import com.robsartin.contactotomy.core.matcher.NicknameDictionary
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds merge-review state built from the imported contacts via the core engine. */
class MergeReviewStore(
    private val contacts: List<Contact>,
    private val matcher: ContactMatcher = defaultMatcher(),
    private val merger: ContactMerger = ContactMerger(),
) {
    private val _state = MutableStateFlow(MergeReviewState(items = buildItems()))
    val state: StateFlow<MergeReviewState> = _state.asStateFlow()

    private fun buildItems(): List<ReviewItem> {
        val result = matcher.match(contacts)
        val high =
            result.clusters.map { cluster ->
                ReviewItem(id = cluster.id, origin = Origin.HIGH, proposal = merger.merge(cluster))
            }
        val uncertain =
            result.uncertainPairs.map { edge ->
                val cluster =
                    Cluster(
                        id = "uncertain-${edge.a.id}+${edge.b.id}",
                        members = listOf(edge.a, edge.b),
                        confidence = Confidence.UNCERTAIN,
                        reasons = edge.reasons,
                    )
                ReviewItem(id = cluster.id, origin = Origin.UNCERTAIN, proposal = merger.merge(cluster))
            }
        return high + uncertain
    }

    fun accept(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.ACCEPT) }

    fun reject(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.REJECT) }

    fun undo(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.PENDING) }

    fun chooseName(
        itemId: String,
        memberId: String,
    ) = updateItem(itemId) { it.copy(nameChoiceId = memberId) }

    fun toggleField(
        itemId: String,
        value: ExcludedValue,
    ) = updateItem(itemId) {
        val next = if (value in it.excludedValues) it.excludedValues - value else it.excludedValues + value
        it.copy(excludedValues = next)
    }

    fun chooseConflict(
        itemId: String,
        field: String,
        value: String,
    ) = updateItem(itemId) { it.copy(conflictChoices = it.conflictChoices + (field to value)) }

    fun acceptAllHighConfidence() =
        _state.update { st ->
            st.copy(items = st.items.map { if (it.origin == Origin.HIGH) it.copy(decision = Decision.ACCEPT) else it })
        }

    private fun updateItem(
        itemId: String,
        transform: (ReviewItem) -> ReviewItem,
    ) = _state.update { st ->
        st.copy(items = st.items.map { if (it.id == itemId) transform(it) else it })
    }

    /** Applies accepted merges and returns the resulting contact list; idempotent guard against double-touch. */
    fun commit(): List<Contact> {
        val accepted = _state.value.items.filter { it.decision == Decision.ACCEPT }
        val seen = mutableSetOf<String>()
        val downgraded = mutableSetOf<String>()
        val finalAccepted = mutableListOf<ReviewItem>()
        for (item in accepted) {
            val memberIds =
                item.proposal.cluster.members
                    .map { it.id }
            if (memberIds.any { it in seen }) {
                downgraded += item.id
            } else {
                seen += memberIds
                finalAccepted += item
            }
        }
        val decisions =
            finalAccepted.map {
                MergeDecision(
                    clusterId = it.proposal.cluster.id,
                    action = Action.ACCEPT,
                    excludedValues = it.excludedValues,
                    conflictChoices = it.conflictChoices,
                )
            }
        val result =
            DecisionApplier().applyDecisions(
                contacts,
                finalAccepted.map { it.proposal },
                decisions,
            )
        // Apply per-cluster name overrides (chosen source card's name) — kept in the UI, engine untouched.
        val nameOverrides: Map<String, ContactName> =
            finalAccepted
                .mapNotNull { item ->
                    val memberId = item.nameChoiceId ?: return@mapNotNull null
                    val member =
                        item.proposal.cluster.members
                            .firstOrNull { it.id == memberId } ?: return@mapNotNull null
                    item.proposal.merged.id to member.name
                }.toMap()
        val withNames = result.map { c -> nameOverrides[c.id]?.let { c.copy(name = it) } ?: c }

        _state.update { st ->
            st.copy(
                items = st.items.map { if (it.id in downgraded) it.copy(decision = Decision.PENDING) else it },
                committed = true,
            )
        }
        return withNames
    }

    companion object {
        fun defaultMatcher(): ContactMatcher = ContactMatcher(EdgeClassifier(NameMatcher(NicknameDictionary.fromResource())))
    }
}

package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.Action
import com.robsartin.contactotomy.core.apply.DecisionApplier
import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.apply.MergeDecision
import com.robsartin.contactotomy.core.company.CompanyNameDetector
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
                reviewItem(id = cluster.id, origin = Origin.HIGH, cluster = cluster)
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
                reviewItem(id = cluster.id, origin = Origin.UNCERTAIN, cluster = cluster)
            }
        return high + uncertain
    }

    fun accept(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.ACCEPT) }

    fun reject(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.REJECT) }

    fun undo(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.PENDING) }

    fun chooseName(
        itemId: String,
        memberId: String,
    ) = updateItem(itemId) { it.copy(nameChoiceId = memberId, nameCleared = false) }

    fun clearName(itemId: String) = updateItem(itemId) { it.copy(nameCleared = true, nameChoiceId = null) }

    fun chooseOrg(
        itemId: String,
        value: String,
    ) = updateItem(itemId) { it.copy(orgChoice = value) }

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

    /** Contacts not already a member of any current review item — the manual-merge pool. */
    fun eligibleForManualMerge(): List<Contact> {
        val claimed =
            _state.value.items
                .flatMap { it.proposal.cluster.members }
                .map { it.id }
                .toSet()
        return contacts.filter { it.id !in claimed }
    }

    /**
     * Force-merges [memberIds] (>= 2 eligible contacts) into a new PENDING MANUAL item.
     * Returns the new item id, or null if fewer than two eligible contacts were given.
     */
    fun manualMerge(memberIds: List<String>): String? {
        val eligible = eligibleForManualMerge().associateBy { it.id }
        val members = memberIds.distinct().mapNotNull { eligible[it] }
        if (members.size < 2) return null
        // No id collision with an auto cluster: the merged contact's id derives from member
        // ids, and eligibility (un-clustered members only) + commit()'s double-touch guard
        // ensure the same member set is never in two clusters at once.
        val cluster =
            Cluster(
                id = "manual-" + members.map { it.id }.sorted().joinToString("+"),
                members = members,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
            )
        val item = reviewItem(id = cluster.id, origin = Origin.MANUAL, cluster = cluster)
        _state.update { st -> st.copy(items = st.items + item) }
        return item.id
    }

    /** Builds a ReviewItem for a cluster, applying company auto-suggest defaults. */
    private fun reviewItem(
        id: String,
        origin: Origin,
        cluster: Cluster,
    ): ReviewItem {
        val suggest = companyAutoSuggest(cluster.members)
        return ReviewItem(
            id = id,
            origin = origin,
            proposal = merger.merge(cluster),
            nameChoiceId = suggest.nameChoiceId,
            nameCleared = suggest.nameCleared,
            orgChoice = suggest.orgChoice,
        )
    }

    private data class AutoSuggest(
        val nameChoiceId: String?,
        val nameCleared: Boolean,
        val orgChoice: String?,
    )

    /**
     * When no member has an org but some member's name looks like a company, suggest promoting that
     * name into org. If a different member has a non-company name, use it as the person name;
     * otherwise (company-only) clear the name.
     */
    private fun companyAutoSuggest(members: List<Contact>): AutoSuggest {
        if (members.any { !it.org.isNullOrBlank() }) return AutoSuggest(nameChoiceId = null, nameCleared = false, orgChoice = null)
        val companyMembers = members.mapNotNull { m -> CompanyNameDetector.detect(m.name)?.let { m to it } }
        if (companyMembers.isEmpty()) return AutoSuggest(nameChoiceId = null, nameCleared = false, orgChoice = null)
        val company = companyMembers.minBy { it.second.ordinal }.first
        val personId =
            members
                .firstOrNull {
                    it.id != company.id && CompanyNameDetector.detect(it.name) == null && displayName(it.name).isNotBlank()
                }?.id
        return if (personId != null) {
            AutoSuggest(nameChoiceId = personId, nameCleared = false, orgChoice = displayName(company.name))
        } else {
            AutoSuggest(nameChoiceId = null, nameCleared = true, orgChoice = displayName(company.name))
        }
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
                    when {
                        item.nameCleared -> item.proposal.merged.id to ContactName()
                        item.nameChoiceId != null -> {
                            val member =
                                item.proposal.cluster.members
                                    .firstOrNull { it.id == item.nameChoiceId }
                            member?.let { item.proposal.merged.id to it.name }
                        }
                        else -> null
                    }
                }.toMap()
        val withNames = result.map { c -> nameOverrides[c.id]?.let { c.copy(name = it) } ?: c }
        // Apply per-cluster org overrides (chosen company/org, or "" to clear) — engine untouched.
        // orgChoice is the single source of truth for the merged org; it supersedes any
        // conflictChoices["org"] (the UI routes org through chooseOrg, not chooseConflict).
        val orgOverrides: Map<String, String> =
            finalAccepted.mapNotNull { item -> item.orgChoice?.let { item.proposal.merged.id to it } }.toMap()
        val withOrg =
            withNames.map { c -> orgOverrides[c.id]?.let { oc -> c.copy(org = oc.ifEmpty { null }) } ?: c }

        _state.update { st ->
            st.copy(
                items = st.items.map { if (it.id in downgraded) it.copy(decision = Decision.PENDING) else it },
                committed = true,
            )
        }
        return withOrg
    }

    companion object {
        fun defaultMatcher(): ContactMatcher = ContactMatcher(EdgeClassifier(NameMatcher(NicknameDictionary.fromResource())))
    }
}

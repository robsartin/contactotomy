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
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.core.normalize.PhoneNormalizer
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

    fun deleteItem(itemId: String) = _state.update { st -> st.copy(items = st.items.filterNot { it.id == itemId }) }

    /**
     * Discards the cluster for [itemId]: marks its decision as [Decision.DISCARD] and records
     * all cluster member ids in [MergeReviewState.discardedIds]. On [commit], those contacts
     * are excluded from the output entirely (neither merged nor passed through).
     *
     * Distinct from [deleteItem] (which removes the suggestion from the list) and
     * [reject] (which keeps members separate in the output).
     */
    fun discardItem(itemId: String) =
        _state.update { st ->
            val item = st.items.firstOrNull { it.id == itemId } ?: return@update st
            val memberIds =
                item.proposal.cluster.members
                    .map { it.id }
                    .toSet()
            st.copy(
                items = st.items.map { if (it.id == itemId) it.copy(decision = Decision.DISCARD) else it },
                discardedIds = st.discardedIds + memberIds,
            )
        }

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
    ) = updateItem(itemId) {
        it.copy(
            conflictChoices = it.conflictChoices + (field to value),
            clearedConflicts = it.clearedConflicts - field,
        )
    }

    fun clearConflict(
        itemId: String,
        field: String,
    ) = updateItem(itemId) { it.copy(clearedConflicts = it.clearedConflicts + field) }

    // ---- Override-layer intents ----

    /**
     * Sets one component of [itemId]'s name override.  The override is seeded from the
     * item's current *effective* name (what commit() would currently produce) so that
     * untouched components are preserved.
     */
    fun setNameComponent(
        itemId: String,
        component: NameComponent,
        value: String,
    ) = updateItem(itemId) { item ->
        val seed = item.nameOverride ?: effectiveName(item)
        val updated =
            when (component) {
                NameComponent.PREFIX -> seed.copy(prefix = value.ifBlank { null })
                NameComponent.GIVEN -> seed.copy(given = value.ifBlank { null })
                NameComponent.MIDDLE -> seed.copy(middle = value.ifBlank { null })
                NameComponent.FAMILY -> seed.copy(family = value.ifBlank { null })
                NameComponent.SUFFIX -> seed.copy(suffix = value.ifBlank { null })
            }
        item.copy(nameOverride = updated)
    }

    fun setNameOverride(
        itemId: String,
        value: ContactName,
    ) = updateItem(itemId) { it.copy(nameOverride = value) }

    fun setOrgOverride(
        itemId: String,
        value: String?,
    ) = updateItem(itemId) { it.copy(orgOverride = value) }

    fun setNotesOverride(
        itemId: String,
        value: String?,
    ) = updateItem(itemId) { it.copy(notesOverride = value) }

    /**
     * Sets [notesOverride] to the newline-joined non-blank notes of all cluster members,
     * deduplicated (order-preserved).
     */
    fun appendSourceNotes(itemId: String) =
        updateItem(itemId) { item ->
            val seen = LinkedHashSet<String>()
            item.proposal.cluster.members
                .mapNotNull { it.notes?.takeIf { n -> n.isNotBlank() } }
                .forEach { seen.add(it) }
            val joined = seen.joinToString("\n").ifBlank { null }
            item.copy(notesOverride = joined)
        }

    fun addPhone(
        itemId: String,
        value: String,
    ) = updateItem(itemId) { item ->
        if (value in item.addedPhones) item else item.copy(addedPhones = item.addedPhones + value)
    }

    fun removeAddedPhone(
        itemId: String,
        value: String,
    ) = updateItem(itemId) { it.copy(addedPhones = it.addedPhones - value) }

    fun addEmail(
        itemId: String,
        value: String,
    ) = updateItem(itemId) { item ->
        if (value in item.addedEmails) item else item.copy(addedEmails = item.addedEmails + value)
    }

    fun removeAddedEmail(
        itemId: String,
        value: String,
    ) = updateItem(itemId) { it.copy(addedEmails = it.addedEmails - value) }

    /**
     * The pre-override effective NAME for [item] — the name commit() produces from the
     * pick/clear controls before any [ReviewItem.nameOverride] is applied. Single source of
     * truth shared by commit() and the prefill of the editable name fields.
     */
    fun effectiveName(item: ReviewItem): ContactName =
        when {
            item.nameCleared -> ContactName()
            item.nameChoiceId != null ->
                item.proposal.cluster.members
                    .firstOrNull { it.id == item.nameChoiceId }
                    ?.name
                    ?: item.proposal.merged.name
            else -> item.proposal.merged.name
        }

    /**
     * The pre-override effective ORG for [item] (empty string means "no org") — mirrors
     * commit(): the org choice supersedes the merged org. Single source of truth shared by
     * commit() and the prefill of the editable org field.
     */
    fun effectiveOrg(item: ReviewItem): String = item.orgChoice ?: item.proposal.merged.org ?: ""

    /**
     * The pre-override effective NOTES for [item] (empty string means "no notes") — mirrors
     * commit(): a cleared conflict wins, else an explicit conflict choice, else the merged
     * notes. Single source of truth shared by commit() and the prefill of the editable notes field.
     */
    fun effectiveNotes(item: ReviewItem): String =
        when {
            "notes" in item.clearedConflicts -> ""
            item.conflictChoices.containsKey("notes") -> item.conflictChoices["notes"] ?: ""
            else -> {
                val notesConflict = item.proposal.conflicts.firstOrNull { it.field == "notes" }
                notesConflict?.chosen ?: item.proposal.merged.notes ?: ""
            }
        }

    /**
     * Returns the exact [Contact] that [commit()] would produce for this accepted item.
     * This is the single source of truth: [commit()] calls this for every accepted item,
     * and the preview card in the UI reads this too — they cannot drift.
     *
     * The logic mirrors the per-item transform inside [commit()]: apply field exclusions and
     * conflict choices on top of [proposal.merged], then layer the effective name/org/notes
     * helpers, then the explicit override layer last.
     */
    fun previewContact(item: ReviewItem): Contact {
        // Step 1: start from proposal.merged and apply exclusions + conflict choices
        // (mirrors DecisionApplier.adjust for this single item)
        var out = item.proposal.merged
        for ((field, value) in item.conflictChoices) {
            out = setSingle(out, field, value)
        }
        for (excluded in item.excludedValues) {
            out = removeValue(out, excluded.field, excluded.value)
        }
        // Step 2: effective name / org / notes (pick/clear/conflict choices)
        out = out.copy(name = effectiveName(item))
        out = out.copy(org = effectiveOrg(item).ifEmpty { null })
        out = out.copy(notes = effectiveNotes(item).ifEmpty { null })
        // title clearing
        if ("title" in item.clearedConflicts) out = out.copy(title = null)
        // Step 3: explicit override layer wins last
        item.nameOverride?.let { out = out.copy(name = it) }
        item.orgOverride?.let { out = out.copy(org = it.ifBlank { null }) }
        item.notesOverride?.let { out = out.copy(notes = it.ifBlank { null }) }
        // Step 4: added phones/emails
        if (item.addedPhones.isNotEmpty()) out = out.copy(phones = (out.phones + item.addedPhones).distinct())
        if (item.addedEmails.isNotEmpty()) out = out.copy(emails = (out.emails + item.addedEmails).distinct())
        return out
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
    ): Contact {
        val phoneNormalizer = PhoneNormalizer()
        return when (field) {
            "phones" ->
                c.copy(
                    phones = c.phones - value,
                    rawPhones = c.rawPhones.filterNot { phoneNormalizer.normalize(it) == value },
                )
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

    /**
     * Applies accepted merges and returns the resulting contact list; idempotent guard against
     * double-touch. Each accepted item's contact is produced by [previewContact], so the preview
     * card and the committed result are provably identical.
     */
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
        // Decisions drive DecisionApplier only for ordering/pass-through logic.
        // Per-item field content is produced by previewContact (single source of truth).
        val decisions =
            finalAccepted.map {
                MergeDecision(
                    clusterId = it.proposal.cluster.id,
                    action = Action.ACCEPT,
                    excludedValues = it.excludedValues,
                    conflictChoices = it.conflictChoices,
                )
            }
        val rawResult =
            DecisionApplier().applyDecisions(
                contacts,
                finalAccepted.map { it.proposal },
                decisions,
            )
        // Replace each accepted-item contact with the result of previewContact, which is the
        // single source of truth for what the merged card looks like.
        val previewById = finalAccepted.associate { it.proposal.merged.id to previewContact(it) }
        val result = rawResult.map { c -> previewById[c.id] ?: c }

        // Exclude contacts whose ids are in discardedIds from the final output.
        val discarded = _state.value.discardedIds
        val withoutDiscarded = result.filterNot { it.id in discarded }

        _state.update { st ->
            st.copy(
                items = st.items.map { if (it.id in downgraded) it.copy(decision = Decision.PENDING) else it },
                committed = true,
            )
        }
        return withoutDiscarded
    }

    companion object {
        fun defaultMatcher(): ContactMatcher = ContactMatcher(EdgeClassifier(NameMatcher(NicknameDictionary.fromResource())))
    }
}

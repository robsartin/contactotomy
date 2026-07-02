package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.company.CompanyNameDetector
import com.robsartin.contactotomy.core.company.CompanyNormalizer
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The transform that will be applied to a marked singleton in Section 2. */
enum class TidyAction { COMPANY, EMAIL_NAME }

/**
 * Composes [MergeReviewStore] (Section 1: duplicates) with singleton-tidy state
 * (Section 2: cards to clean).
 *
 * A *singleton* is a contact whose id is NOT a member of any HIGH cluster and NOT in
 * any uncertain pair (i.e. it never appears in Section 1). A singleton is *suggested*
 * when [CompanyNameDetector.isHighPrecision] returns true, or when the card has no
 * company-name text, no org, and at least one email.
 *
 * [commit] chains: mergeStore.commit() first, then applies the tidy transforms over
 * the markedIds in the result.
 */
class ReviewStore(
    contacts: List<Contact>,
) {
    /** Exposed so the screen (and tests) can drive Section 1 directly. */
    val mergeStore = MergeReviewStore(contacts)

    // IDs of all contacts that appear in any merge group (Section 1).
    private val groupedIds: Set<String> =
        mergeStore.state.value.items
            .flatMap { item ->
                item.proposal.cluster.members
                    .map { it.id }
            }.toSet()

    // Section 2: singletons that are suggested for cleaning.
    private val singletons: List<Contact> = contacts.filter { it.id !in groupedIds }

    private val suggested: Set<String> =
        singletons.filter { suggested(it) }.map { it.id }.toSet()

    private val _markedIds = MutableStateFlow(suggested)

    /** Current set of marked singleton ids for Section 2. */
    val markedIds: Set<String> get() = _markedIds.value

    /** All suggested singletons (Section 2 content). */
    fun cleanCandidates(): List<Contact> = singletons.filter { suggested(it) }

    /** Flip the mark for a singleton. */
    fun toggleClean(id: String) = _markedIds.update { ids -> if (id in ids) ids - id else ids + id }

    /** The transform a card would get if marked. */
    fun actionFor(contact: Contact): TidyAction =
        if (companyNameText(contact.name).isBlank() && contact.org.isNullOrBlank() && contact.emails.isNotEmpty()) {
            TidyAction.EMAIL_NAME
        } else {
            TidyAction.COMPANY
        }

    /**
     * Section 1 result chained into Section 2 transforms.
     * 1. mergeStore.commit() applies merges; singletons pass through untouched (their ids survive).
     * 2. For each contact in the result whose id is in markedIds, apply the tidy transform.
     */
    fun commit(): List<Contact> {
        val merged = mergeStore.commit()
        val marked = _markedIds.value
        return merged.map { c ->
            if (c.id !in marked) {
                c
            } else {
                when (actionFor(c)) {
                    TidyAction.EMAIL_NAME -> CompanyNormalizer.nameFromEmail(c)
                    TidyAction.COMPANY -> CompanyNormalizer.markAsCompany(c)
                }
            }
        }
    }

    /** True if the contact should be pre-marked in Section 2. */
    private fun suggested(c: Contact): Boolean =
        CompanyNameDetector.isHighPrecision(c.name) ||
            (companyNameText(c.name).isBlank() && c.org.isNullOrBlank() && c.emails.isNotEmpty())
}

package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.merger.MergeProposal
import com.robsartin.contactotomy.core.model.ContactName

enum class Origin { HIGH, UNCERTAIN, MANUAL }

enum class Decision { PENDING, ACCEPT, REJECT }

/** Which component of a [ContactName] to update with [MergeReviewStore.setNameComponent]. */
enum class NameComponent { PREFIX, GIVEN, MIDDLE, FAMILY, SUFFIX }

data class ReviewItem(
    val id: String,
    val origin: Origin,
    val proposal: MergeProposal,
    val decision: Decision = Decision.PENDING,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
    val nameChoiceId: String? = null,
    val nameCleared: Boolean = false,
    val orgChoice: String? = null,
    val clearedConflicts: Set<String> = emptySet(),
    // Override layer — applied last in commit(); null means "no override, use existing logic".
    val nameOverride: ContactName? = null,
    val orgOverride: String? = null,
    val notesOverride: String? = null,
    val addedPhones: List<String> = emptyList(),
    val addedEmails: List<String> = emptyList(),
)

data class MergeReviewState(
    val items: List<ReviewItem> = emptyList(),
    val committed: Boolean = false,
)

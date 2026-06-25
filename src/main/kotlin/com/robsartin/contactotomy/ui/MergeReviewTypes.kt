package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.merger.MergeProposal

enum class Origin { HIGH, UNCERTAIN, MANUAL }

enum class Decision { ACCEPT, REJECT, SKIP }

data class ReviewItem(
    val id: String,
    val origin: Origin,
    val proposal: MergeProposal,
    val decision: Decision,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
)

data class MergeReviewState(
    val items: List<ReviewItem> = emptyList(),
    val committed: Boolean = false,
)

package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.Flagged
import com.robsartin.contactotomy.core.rules.Rule

data class RuleToggle(
    val rule: Rule,
    val enabled: Boolean = true,
)

data class DeletionReviewState(
    val rules: List<RuleToggle>,
    val flagged: List<Flagged> = emptyList(),
    val approvedIds: Set<String> = emptySet(),
    val totalContacts: Int = 0,
    val hasRun: Boolean = false,
    val committed: Boolean = false,
)

/**
 * Pure summary of the current deletion preview: how many contacts are flagged,
 * approved for deletion, and will remain.  Also provides per-rule match counts.
 */
data class DeletionPreviewSummary(
    val flagged: Int,
    val approved: Int,
    val remaining: Int,
    /** Map of rule name → number of flagged contacts that matched that rule. */
    val perRuleCounts: Map<String, Int>,
)

/**
 * Computes a [DeletionPreviewSummary] from a [DeletionReviewState].
 * Pure function — no side effects, safe to call in unit tests.
 */
fun deletionPreviewSummary(state: DeletionReviewState): DeletionPreviewSummary {
    val flaggedCount = state.flagged.size
    val approvedCount = state.approvedIds.size
    val remaining = state.totalContacts - approvedCount
    val perRuleCounts =
        state.flagged
            .flatMap { f -> f.matches.map { it.ruleName } }
            .groupingBy { it }
            .eachCount()
    return DeletionPreviewSummary(
        flagged = flaggedCount,
        approved = approvedCount,
        remaining = remaining,
        perRuleCounts = perRuleCounts,
    )
}

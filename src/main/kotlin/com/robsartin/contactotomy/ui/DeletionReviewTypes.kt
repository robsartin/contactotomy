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

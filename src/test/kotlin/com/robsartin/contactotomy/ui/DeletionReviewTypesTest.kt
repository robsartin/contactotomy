package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.Predicate
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewTypesTest {
    @Test
    fun `rule toggle defaults to enabled`() {
        val t = RuleToggle(Rule("no email", Predicate(PredicateKind.NO_EMAIL)))
        assertTrue(t.enabled)
    }

    @Test
    fun `state carries rules, flagged, approvals and totals`() {
        val state = DeletionReviewState(rules = emptyList(), totalContacts = 5)
        assertEquals(5, state.totalContacts)
        assertEquals(emptySet(), state.approvedIds)
        assertEquals(false, state.hasRun)
    }
}

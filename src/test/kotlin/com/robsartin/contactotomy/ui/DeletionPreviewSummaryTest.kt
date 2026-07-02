package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.Flagged
import com.robsartin.contactotomy.core.rules.RuleMatch
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class DeletionPreviewSummaryTest {
    private fun flagged(
        id: String,
        ruleName: String = "rule1",
    ) = Flagged(
        contact = contact(id, given = "Test$id"),
        matches = listOf(RuleMatch(ruleName = ruleName, reason = "test reason")),
    )

    @Test
    fun `summary returns zeros before any flagged`() {
        val state =
            DeletionReviewState(
                rules = emptyList(),
                flagged = emptyList(),
                approvedIds = emptySet(),
                totalContacts = 10,
            )
        val summary = deletionPreviewSummary(state)
        assertEquals(0, summary.flagged)
        assertEquals(0, summary.approved)
        assertEquals(10, summary.remaining)
    }

    @Test
    fun `summary counts flagged and approved correctly`() {
        val f1 = flagged("c1", "rule1")
        val f2 = flagged("c2", "rule1")
        val f3 = flagged("c3", "rule2")
        val state =
            DeletionReviewState(
                rules = emptyList(),
                flagged = listOf(f1, f2, f3),
                approvedIds = setOf("c1", "c3"),
                totalContacts = 100,
            )
        val summary = deletionPreviewSummary(state)
        assertEquals(3, summary.flagged)
        assertEquals(2, summary.approved)
        assertEquals(98, summary.remaining)
    }

    @Test
    fun `summary remaining is totalContacts minus approved count`() {
        val f = flagged("c1")
        val state =
            DeletionReviewState(
                rules = emptyList(),
                flagged = listOf(f),
                approvedIds = setOf("c1"),
                totalContacts = 50,
            )
        val summary = deletionPreviewSummary(state)
        assertEquals(50 - 1, summary.remaining)
    }

    @Test
    fun `perRuleCounts returns count per rule name`() {
        val f1 = flagged("c1", "alpha")
        val f2 = flagged("c2", "alpha")
        val f3 = flagged("c3", "beta")
        val state =
            DeletionReviewState(
                rules = emptyList(),
                flagged = listOf(f1, f2, f3),
                approvedIds = emptySet(),
                totalContacts = 10,
            )
        val counts = deletionPreviewSummary(state).perRuleCounts
        assertEquals(2, counts["alpha"])
        assertEquals(1, counts["beta"])
    }

    @Test
    fun `perRuleCounts handles contact matching multiple rules`() {
        val f1 =
            Flagged(
                contact = contact("c1", given = "Multi"),
                matches =
                    listOf(
                        RuleMatch(ruleName = "ruleA", reason = "r1"),
                        RuleMatch(ruleName = "ruleB", reason = "r2"),
                    ),
            )
        val state =
            DeletionReviewState(
                rules = emptyList(),
                flagged = listOf(f1),
                approvedIds = emptySet(),
                totalContacts = 5,
            )
        val counts = deletionPreviewSummary(state).perRuleCounts
        assertEquals(1, counts["ruleA"])
        assertEquals(1, counts["ruleB"])
    }

    @Test
    fun `approved cannot exceed flagged`() {
        val f = flagged("c1")
        val state =
            DeletionReviewState(
                rules = emptyList(),
                flagged = listOf(f),
                approvedIds = setOf("c1"),
                totalContacts = 5,
            )
        val summary = deletionPreviewSummary(state)
        assertEquals(1, summary.flagged)
        assertEquals(1, summary.approved)
        assertEquals(4, summary.remaining)
    }
}

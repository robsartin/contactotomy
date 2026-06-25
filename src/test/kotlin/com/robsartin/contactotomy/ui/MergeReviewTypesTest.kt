package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeReviewTypesTest {
    @Test
    fun `review item carries origin, decision, exclusions and conflict choices`() {
        val proposal =
            ContactMerger().merge(
                Cluster("c1", listOf(contact("a"), contact("b")), Confidence.HIGH, emptyList()),
            )
        val item =
            ReviewItem(
                id = "c1",
                origin = Origin.HIGH,
                proposal = proposal,
                decision = Decision.ACCEPT,
                excludedValues = setOf(ExcludedValue("phones", "+1")),
                conflictChoices = mapOf("org" to "Acme"),
            )
        assertEquals(Origin.HIGH, item.origin)
        assertEquals(Decision.ACCEPT, item.decision)
        assertEquals("Acme", item.conflictChoices["org"])
    }
}

package com.robsartin.contactotomy.core.apply

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.merger.MergeProposal
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class DecisionApplierRawPhonesTest {
    private fun contact(
        id: String,
        phones: List<String>,
        rawPhones: List<String>,
    ) = Contact(
        id = id,
        source = Source.APPLE,
        name = ContactName(given = id),
        phones = phones,
        rawPhones = rawPhones,
        rawVCard = "",
    )

    @Test
    fun `excluding a normalized phone also drops the matching rawPhones original`() {
        val a = contact("a", phones = listOf("+15125551234"), rawPhones = listOf("(512) 555-1234"))
        val b = contact("b", phones = listOf("+15125551234"), rawPhones = listOf("512.555.1234"))
        val merged =
            contact(
                "merged",
                phones = listOf("+15125551234"),
                rawPhones = listOf("(512) 555-1234", "512.555.1234"),
            )
        val cluster = Cluster("c1", listOf(a, b), Confidence.HIGH, emptyList())
        val proposal = MergeProposal(cluster, merged, provenance = emptyList(), conflicts = emptyList())
        val decision =
            MergeDecision(
                clusterId = "c1",
                action = Action.ACCEPT,
                excludedValues = setOf(ExcludedValue("phones", "+15125551234")),
            )

        val result = DecisionApplier().applyDecisions(listOf(a, b), listOf(proposal), listOf(decision)).single()

        assertEquals(emptyList(), result.phones)
        assertEquals(emptyList(), result.rawPhones)
    }
}

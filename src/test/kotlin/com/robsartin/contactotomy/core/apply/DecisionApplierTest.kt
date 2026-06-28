package com.robsartin.contactotomy.core.apply

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.core.merger.MergeProposal
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionApplierTest {
    private val applier = DecisionApplier()
    private val merger = ContactMerger()

    private fun proposalFor(vararg members: Contact): MergeProposal =
        merger.merge(
            Cluster(
                "cluster-" + members.map { it.id }.sorted().joinToString("+"),
                members.toList(),
                Confidence.HIGH,
                listOf(MatchReason.SHARED_PHONE),
            ),
        )

    @Test
    fun `accept replaces members with merged card and keeps singletons`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val c = contact("c", given = "Zoe", family = "Quinn")
        val p = proposalFor(a, b)

        val result =
            applier.applyDecisions(
                listOf(a, b, c),
                listOf(p),
                listOf(MergeDecision(p.cluster.id, Action.ACCEPT)),
            )

        assertEquals(listOf(p.merged.id, "c"), result.map { it.id })
        assertEquals(setOf("+1111", "+2222"), result.first().phones.toSet())
    }

    @Test
    fun `no decision defaults to reject, leaving members intact`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val p = proposalFor(a, b)
        val result = applier.applyDecisions(listOf(a, b), listOf(p), emptyList())
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `excluded value is dropped from the merged card`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val p = proposalFor(a, b)
        val result =
            applier.applyDecisions(
                listOf(a, b),
                listOf(p),
                listOf(MergeDecision(p.cluster.id, Action.ACCEPT, excludedValues = setOf(ExcludedValue("phones", "+1111")))),
            )
        assertTrue("+1111" !in result.first().phones)
        assertTrue("+2222" in result.first().phones)
    }

    @Test
    fun `excluding a URL removes it from the merged card`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"), urls = listOf("https://rob.example.com"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"), urls = listOf("https://robert.example.com"))
        val p = proposalFor(a, b)
        val result =
            applier.applyDecisions(
                listOf(a, b),
                listOf(p),
                listOf(
                    MergeDecision(
                        p.cluster.id,
                        Action.ACCEPT,
                        excludedValues = setOf(ExcludedValue("urls", "https://rob.example.com")),
                    ),
                ),
            )
        assertTrue("https://rob.example.com" !in result.first().urls)
        assertTrue("https://robert.example.com" in result.first().urls)
    }

    @Test
    fun `excluding an address display string removes the matching PostalAddress`() {
        val addr1 = PostalAddress(street = "100 Main St", city = "Austin", region = "TX", postalCode = "78701")
        val addr2 = PostalAddress(street = "200 Oak Ave", city = "Seattle", region = "WA", postalCode = "98101")
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"), addresses = listOf(addr1))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"), addresses = listOf(addr2))
        val p = proposalFor(a, b)
        val displayStr = addr1.toDisplayString()
        val result =
            applier.applyDecisions(
                listOf(a, b),
                listOf(p),
                listOf(
                    MergeDecision(
                        p.cluster.id,
                        Action.ACCEPT,
                        excludedValues = setOf(ExcludedValue("addresses", displayStr)),
                    ),
                ),
            )
        assertTrue(result.first().addresses.none { it.toDisplayString() == displayStr })
        assertTrue(result.first().addresses.any { it.toDisplayString() == addr2.toDisplayString() })
    }

    @Test
    fun `conflict choice overrides the merged single-value field`() {
        val a = contact("a", given = "Rob", family = "Sartin", org = "OldCo")
        val b = contact("b", given = "Robert", family = "Sartin", org = "NewCo")
        val p = proposalFor(a, b)
        val result =
            applier.applyDecisions(
                listOf(a, b),
                listOf(p),
                listOf(MergeDecision(p.cluster.id, Action.ACCEPT, conflictChoices = mapOf("org" to "OldCo"))),
            )
        assertEquals("OldCo", result.first().org)
    }

    @Test
    fun `output is deterministic`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val p = proposalFor(a, b)
        val decisions = listOf(MergeDecision(p.cluster.id, Action.ACCEPT))
        assertEquals(
            applier.applyDecisions(listOf(a, b), listOf(p), decisions),
            applier.applyDecisions(listOf(a, b), listOf(p), decisions),
        )
    }
}

package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactMergerAddressTest {
    private val merger = ContactMerger()

    private fun cluster(vararg members: Contact) = Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    private fun contactWithAddresses(
        id: String,
        vararg addresses: PostalAddress,
    ) = contact(id, given = "Test", family = "Person").copy(addresses = addresses.toList())

    @Test
    fun `unions distinct addresses from multiple members`() {
        val addr1 = PostalAddress(street = "100 Main St", city = "Austin", region = "TX", postalCode = "78701")
        val addr2 = PostalAddress(street = "200 Oak Ave", city = "Seattle", region = "WA", postalCode = "98101")

        val a = contactWithAddresses("a", addr1)
        val b = contactWithAddresses("b", addr2)

        val merged = merger.merge(cluster(a, b)).merged

        assertEquals(2, merged.addresses.size)
        assertEquals(setOf(addr1, addr2), merged.addresses.toSet())
    }

    @Test
    fun `deduplicates identical addresses across members`() {
        val addr = PostalAddress(street = "123 Same St", city = "Portland", region = "OR", postalCode = "97201")

        val a = contactWithAddresses("a", addr)
        val b = contactWithAddresses("b", addr)

        val merged = merger.merge(cluster(a, b)).merged

        assertEquals(1, merged.addresses.size)
        assertEquals(addr, merged.addresses.single())
    }

    @Test
    fun `contact with no addresses merges cleanly with one that has addresses`() {
        val addr = PostalAddress(street = "55 Unique Blvd", city = "Denver", region = "CO", postalCode = "80201")

        val withAddr = contactWithAddresses("a", addr)
        val noAddr = contactWithAddresses("b")

        val merged = merger.merge(cluster(withAddr, noAddr)).merged

        assertEquals(listOf(addr), merged.addresses)
    }

    @Test
    fun `preserves primary order then appends secondary unique addresses`() {
        val primary = PostalAddress(street = "1 Primary St", city = "Boston", region = "MA", postalCode = "02101")
        val secondary = PostalAddress(street = "2 Secondary Ave", city = "Chicago", region = "IL", postalCode = "60601")

        // newer = primary in merger (sorted by modifiedAt desc)
        val newer =
            contactWithAddresses("b", primary).copy(
                modifiedAt = java.time.Instant.parse("2024-01-01T00:00:00Z"),
            )
        val older =
            contactWithAddresses("a", secondary).copy(
                modifiedAt = java.time.Instant.parse("2020-01-01T00:00:00Z"),
            )

        val merged = merger.merge(cluster(older, newer)).merged

        // primary's address comes first (primary member is processed first in union)
        assertEquals(primary, merged.addresses.first())
        assertEquals(secondary, merged.addresses[1])
    }
}

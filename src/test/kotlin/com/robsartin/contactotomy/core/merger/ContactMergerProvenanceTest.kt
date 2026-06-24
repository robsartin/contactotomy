package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactMergerProvenanceTest {
    private val merger = ContactMerger()

    private fun cluster(vararg members: Contact) = Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    @Test
    fun `records a conflict for disagreeing single-value field with newest chosen`() {
        val older =
            contact(
                "a",
                given = "Robert",
                family = "Sartin",
                org = "OldCo",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val newer =
            contact(
                "b",
                given = "Robert",
                family = "Sartin",
                org = "NewCo",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val proposal = merger.merge(cluster(older, newer))
        val orgConflict = proposal.conflicts.single { it.field == "org" }
        assertEquals("NewCo", orgConflict.chosen)
        assertEquals(setOf("OldCo", "NewCo"), orgConflict.candidates.map { it.value }.toSet())
    }

    @Test
    fun `no conflict when single-value field agrees or only one present`() {
        val a = contact("a", given = "Robert", family = "Sartin", org = "Acme")
        val b = contact("b", given = "Robert", family = "Sartin", org = "Acme")
        assertTrue(merger.merge(cluster(a, b)).conflicts.none { it.field == "org" })
    }

    @Test
    fun `chooses the most complete name`() {
        val full =
            contact(
                "a",
                given = "Robert",
                middle = "A",
                family = "Sartin",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val sparse =
            contact(
                "b",
                given = "Robert",
                family = "Sartin",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
            ) // newer but less complete
        val merged = merger.merge(cluster(full, sparse)).merged
        assertEquals("A", merged.name.middle)
    }

    @Test
    fun `provenance maps a phone value to its source contact`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Rob", family = "Sartin", phones = listOf("+2222"))
        val prov = merger.merge(cluster(a, b)).provenance
        val p1 = prov.single { it.field == "phones" && it.value == "+1111" }
        assertEquals(listOf("a"), p1.sourceContactIds)
    }

    // contact(...) helper as a private member to avoid colliding with the top-level
    // contact(...) in ContactMergerTest (same package).
    private fun contact(
        id: String,
        given: String? = null,
        middle: String? = null,
        family: String? = null,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        org: String? = null,
        title: String? = null,
        notes: String? = null,
        categories: List<String> = emptyList(),
        modifiedAt: Instant? = null,
        createdAt: Instant? = null,
        source: Source = Source.APPLE,
    ) = Contact(
        id = id,
        source = source,
        name = ContactName(given = given, middle = middle, family = family),
        phones = phones,
        rawPhones = phones,
        emails = emails,
        org = org,
        title = title,
        notes = notes,
        categories = categories,
        modifiedAt = modifiedAt,
        createdAt = createdAt,
        rawVCard = "BEGIN:VCARD\nFN:${given ?: ""} ${family ?: ""}\nEND:VCARD",
    )
}

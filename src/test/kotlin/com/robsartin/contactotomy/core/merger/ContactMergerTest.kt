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

class ContactMergerTest {
    private val merger = ContactMerger()

    private fun cluster(vararg members: Contact) = Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    @Test
    fun `unions multi-value fields and prefers newest for single-value`() {
        val older =
            contact(
                "a",
                given = "Robert",
                family = "Sartin",
                phones = listOf("+1111"),
                emails = listOf("old@x.com"),
                categories = listOf("Work"),
                org = "OldCo",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val newer =
            contact(
                "b",
                given = "Robert",
                family = "Sartin",
                phones = listOf("+2222"),
                emails = listOf("new@x.com"),
                categories = listOf("Friends"),
                org = "NewCo",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        val merged = merger.merge(cluster(older, newer)).merged

        assertEquals(listOf("+2222", "+1111"), merged.phones) // newer (primary) first
        assertEquals(setOf("old@x.com", "new@x.com"), merged.emails.toSet())
        assertEquals(setOf("Work", "Friends"), merged.categories.toSet())
        assertEquals("NewCo", merged.org) // newest non-null
    }

    @Test
    fun `merged id is deterministic from member ids`() {
        val a = contact("a", given = "Rob", family = "Sartin")
        val b = contact("b", given = "Rob", family = "Sartin")
        assertEquals("merged-a+b", merger.merge(cluster(a, b)).merged.id)
        assertEquals("merged-a+b", merger.merge(cluster(b, a)).merged.id)
    }
}

// paste the contact(...) helper here (identical to Task 4)
fun contact(
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

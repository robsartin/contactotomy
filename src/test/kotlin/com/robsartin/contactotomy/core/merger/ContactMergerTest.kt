package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.testsupport.contact
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

    @Test
    fun `dedupes emails case-insensitively preserving first occurrence casing`() {
        val a = contact("a", given = "Rob", emails = listOf("Bob@X.com", "shared@y.com"))
        val b = contact("b", given = "Rob", emails = listOf("bob@x.com", "extra@z.com"))
        val merged = merger.merge(cluster(a, b)).merged
        // "Bob@X.com" from member a comes first; "bob@x.com" is a case variant and should be dropped
        assertEquals(listOf("Bob@X.com", "shared@y.com", "extra@z.com"), merged.emails)
    }
}

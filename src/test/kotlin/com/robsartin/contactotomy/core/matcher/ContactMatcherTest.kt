package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactMatcherTest {
    private val matcher = ContactMatcher(
        EdgeClassifier(NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob")))))
    )

    @Test
    fun `transitive chain over HIGH edges forms one cluster`() {
        // a-b share a phone; b-c share an email; a and c share nothing directly.
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1111"), emails = listOf("r@x.com"))
        val c = contact("c", given = "Bob", family = "Sartin", emails = listOf("r@x.com"))
        val result = matcher.match(listOf(a, b, c))
        assertEquals(1, result.clusters.size)
        assertEquals(setOf("a", "b", "c"), result.clusters.first().members.map { it.id }.toSet())
    }

    @Test
    fun `uncertain name-only pair is not clustered but surfaced`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val result = matcher.match(listOf(a, b))
        assertTrue(result.clusters.isEmpty())
        assertEquals(1, result.uncertainPairs.size)
    }

    @Test
    fun `married couple sharing a phone yields no cluster and no uncertain pair`() {
        val alice = contact("a", given = "Alice", family = "Smith", phones = listOf("+1999"))
        val bob = contact("b", given = "Bob", family = "Smith", phones = listOf("+1999"))
        val result = matcher.match(listOf(alice, bob))
        assertTrue(result.clusters.isEmpty())
        assertTrue(result.uncertainPairs.isEmpty())
    }

    @Test
    fun `cluster id is deterministic from member ids`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1111"))
        assertEquals(
            matcher.match(listOf(a, b)).clusters.first().id,
            matcher.match(listOf(b, a)).clusters.first().id,
        )
    }

    private fun contact(
        id: String,
        given: String? = null, middle: String? = null, family: String? = null,
        phones: List<String> = emptyList(), emails: List<String> = emptyList(),
        org: String? = null, title: String? = null, notes: String? = null,
        categories: List<String> = emptyList(),
        modifiedAt: java.time.Instant? = null, createdAt: java.time.Instant? = null,
        source: Source = Source.APPLE,
    ) = Contact(
        id = id, source = source,
        name = ContactName(given = given, middle = middle, family = family),
        phones = phones, rawPhones = phones, emails = emails, org = org, title = title,
        notes = notes, categories = categories, modifiedAt = modifiedAt, createdAt = createdAt,
        rawVCard = "BEGIN:VCARD\nFN:${given ?: ""} ${family ?: ""}\nEND:VCARD",
    )
}

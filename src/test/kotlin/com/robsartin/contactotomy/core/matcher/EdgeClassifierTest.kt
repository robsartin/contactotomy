package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeClassifierTest {
    private val classifier = EdgeClassifier(NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob")))))

    @Test
    fun `married couple sharing a phone is never merged`() {
        val alice = contact("a", given = "Alice", family = "Smith", phones = listOf("+15125550000"))
        val bob = contact("b", given = "Bob", family = "Smith", phones = listOf("+15125550000"))
        assertNull(classifier.classify(alice, bob))
    }

    @Test
    fun `nickname plus shared phone is HIGH`() {
        val a = contact("a", given = "Bob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val edge = classifier.classify(a, b)!!
        assertEquals(Confidence.HIGH, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.SHARED_PHONE))
        assertTrue(edge.reasons.contains(MatchReason.NAME_NICKNAME))
    }

    @Test
    fun `dropped middle plus shared phone is HIGH`() {
        val a = contact("a", given = "Robert", middle = "A", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        assertEquals(Confidence.HIGH, classifier.classify(a, b)!!.confidence)
    }

    @Test
    fun `surname change with shared email is HIGH and flagged`() {
        val a = contact("a", given = "Jane", family = "Doe", emails = listOf("jane@x.com"))
        val b = contact("b", given = "Jane", family = "Smith", emails = listOf("jane@x.com"))
        val edge = classifier.classify(a, b)!!
        assertEquals(Confidence.HIGH, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.SURNAME_CHANGE))
    }

    @Test
    fun `name only match with no shared contact is UNCERTAIN`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val edge = classifier.classify(a, b)!!
        assertEquals(Confidence.UNCERTAIN, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.NAME_ONLY))
    }

    @Test
    fun `shared phone with one missing given name is UNCERTAIN`() {
        val a = contact("a", phones = listOf("+15125559999"))
        val b = contact("b", given = "Bob", family = "Sartin", phones = listOf("+15125559999"))
        assertEquals(Confidence.UNCERTAIN, classifier.classify(a, b)!!.confidence)
    }

    @Test
    fun `same given different family with no shared contact is not an edge`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Smith")
        assertNull(classifier.classify(a, b))
    }
}

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
    modifiedAt: java.time.Instant? = null,
    createdAt: java.time.Instant? = null,
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

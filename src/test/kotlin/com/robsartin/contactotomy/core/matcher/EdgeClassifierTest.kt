package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeClassifierTest {
    private val classifier = EdgeClassifier(NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob")))))

    // Phase 2: person <-> company-name tests

    @Test
    fun `person plus high-precision company sharing a phone is UNCERTAIN with SHARED_PHONE and COMPANY_MATCH`() {
        val person = contact("p", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val company = contact("c", given = "Acme", family = "Inc", phones = listOf("+15125551234"))
        val edge = classifier.classify(person, company)!!
        assertEquals(Confidence.UNCERTAIN, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.SHARED_PHONE))
        assertTrue(edge.reasons.contains(MatchReason.COMPANY_MATCH))
    }

    @Test
    fun `person plus high-precision company sharing an email is UNCERTAIN with SHARED_EMAIL and COMPANY_MATCH`() {
        val person = contact("p", given = "Robert", family = "Sartin", emails = listOf("rob@example.com"))
        val company = contact("c", given = "Acme", family = "Inc", emails = listOf("rob@example.com"))
        val edge = classifier.classify(person, company)!!
        assertEquals(Confidence.UNCERTAIN, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.SHARED_EMAIL))
        assertTrue(edge.reasons.contains(MatchReason.COMPANY_MATCH))
    }

    @Test
    fun `person plus high-precision company with no shared contact is null`() {
        val person = contact("p", given = "Robert", family = "Sartin")
        val company = contact("c", given = "Acme", family = "Inc")
        assertNull(classifier.classify(person, company))
    }

    @Test
    fun `person plus weak company-ish name sharing a phone falls through to existing rules (null due to given-name conflict)`() {
        // "Acme" alone (no family) is WEAK, so isHighPrecision = false; Phase 2 does not intercept.
        val person = contact("p", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val weakCo = contact("w", given = "Acme", phones = listOf("+15125551234"))
        // given-name conflict "Robert" vs "Acme" => existing Rule 1 returns null
        assertNull(classifier.classify(person, weakCo))
    }

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

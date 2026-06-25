package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CandidateIndexTest {
    private fun idsOf(pairs: Set<Pair<Contact, Contact>>) = pairs.map { setOf(it.first.id, it.second.id) }.toSet()

    @Test
    fun `cards sharing a phone are candidates`() {
        val a = contact("a", given = "Rob", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", phones = listOf("+15125551234"))
        val c = contact("c", given = "Zoe", phones = listOf("+15125559999"))
        val pairs = CandidateIndex(listOf(a, b, c)).candidatePairs()
        assertTrue(idsOf(pairs).contains(setOf("a", "b")))
        assertEquals(1, pairs.size)
    }

    @Test
    fun `cards sharing only a family name are candidates`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Janet", family = "Doe")
        val pairs = CandidateIndex(listOf(a, b)).candidatePairs()
        assertEquals(setOf(setOf("a", "b")), idsOf(pairs))
    }

    @Test
    fun `a pair sharing multiple keys appears only once`() {
        val a = contact("a", given = "Jane", family = "Doe", phones = listOf("+15125551234"), emails = listOf("j@x.com"))
        val b = contact("b", given = "Jane", family = "Doe", phones = listOf("+15125551234"), emails = listOf("j@x.com"))
        val pairs = CandidateIndex(listOf(a, b)).candidatePairs()
        assertEquals(1, pairs.size)
    }

    @Test
    fun `cards sharing nothing are not candidates`() {
        val a = contact("a", given = "Jane", family = "Doe", phones = listOf("+1"))
        val b = contact("b", given = "Mark", family = "Twain", phones = listOf("+2"))
        assertTrue(CandidateIndex(listOf(a, b)).candidatePairs().isEmpty())
    }
}

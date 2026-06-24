package com.robsartin.contactotomy.core.matcher

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NicknameDictionaryTest {
    @Test
    fun `equivalent within a group, case insensitive`() {
        val dict = NicknameDictionary(listOf(setOf("robert", "rob", "bob")))
        assertTrue(dict.areEquivalent("Bob", "Robert"))
        assertTrue(dict.areEquivalent("rob", "bob"))
    }

    @Test
    fun `same string is always equivalent`() {
        val dict = NicknameDictionary(emptyList())
        assertTrue(dict.areEquivalent("Alice", "alice"))
    }

    @Test
    fun `different groups or unknown names are not equivalent`() {
        val dict = NicknameDictionary(listOf(setOf("robert", "bob"), setOf("william", "bill")))
        assertFalse(dict.areEquivalent("bob", "bill"))
        assertFalse(dict.areEquivalent("zaphod", "robert"))
    }

    @Test
    fun `loads bundled resource`() {
        val dict = NicknameDictionary.fromResource()
        assertTrue(dict.areEquivalent("bob", "robert"))
        assertTrue(dict.areEquivalent("liz", "elizabeth"))
    }
}

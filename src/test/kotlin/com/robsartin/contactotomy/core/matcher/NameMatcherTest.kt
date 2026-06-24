package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.ContactName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NameMatcherTest {
    private val matcher = NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob"))))
    private fun name(given: String? = null, middle: String? = null, family: String? = null) =
        ContactName(given = given, middle = middle, family = family)

    @Test
    fun `exact given names match`() {
        assertEquals(MatchReason.NAME_EXACT, matcher.givenMatch(name(given = "Robert"), name(given = "robert")))
    }

    @Test
    fun `nickname given names match`() {
        assertEquals(MatchReason.NAME_NICKNAME, matcher.givenMatch(name(given = "Bob"), name(given = "Robert")))
    }

    @Test
    fun `initial matches full given name`() {
        assertEquals(MatchReason.NAME_INITIAL, matcher.givenMatch(name(given = "R."), name(given = "Robert")))
    }

    @Test
    fun `clearly different given names do not match and conflict`() {
        assertNull(matcher.givenMatch(name(given = "Alice"), name(given = "Bob")))
        assertTrue(matcher.givenConflict(name(given = "Alice"), name(given = "Bob")))
    }

    @Test
    fun `missing given name is never a conflict`() {
        assertFalse(matcher.givenConflict(name(family = "Smith"), name(given = "Bob", family = "Smith")))
    }

    @Test
    fun `family matches when equal or one missing, differs when both present and unequal`() {
        assertTrue(matcher.familyMatches(name(family = "Sartin"), name(family = "sartin")))
        assertTrue(matcher.familyMatches(name(family = "Sartin"), name(given = "Rob")))
        assertFalse(matcher.familyMatches(name(family = "Doe"), name(family = "Smith")))
        assertTrue(matcher.familyDiffers(name(family = "Doe"), name(family = "Smith")))
        assertFalse(matcher.familyDiffers(name(family = "Doe"), name(given = "Jane")))
    }
}

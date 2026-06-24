package com.robsartin.contactotomy.core.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobTest {
    @Test fun `star matches a run`() {
        assertTrue(Glob.matches("*@indeed.com", "bob@indeed.com"))
        assertTrue(Glob.matches("sartin@*", "sartin@gmail.com"))
    }

    @Test fun `question mark matches exactly one char`() {
        assertTrue(Glob.matches("a?c", "abc"))
        assertFalse(Glob.matches("a?c", "ac"))
        assertFalse(Glob.matches("a?c", "abbc"))
    }

    @Test fun `matching is case-insensitive`() {
        assertTrue(Glob.matches("*@INDEED.com", "x@indeed.com"))
    }

    @Test fun `dot is a literal, not any-char`() {
        assertTrue(Glob.matches("a.b", "a.b"))
        assertFalse(Glob.matches("a.b", "axb"))
    }
}

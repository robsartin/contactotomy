package com.contactotomy.core.normalize

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailNormalizerTest {
    @Test
    fun `lowercases and trims`() {
        assertEquals("rob@example.com", EmailNormalizer.normalize("  Rob@Example.COM "))
    }

    @Test
    fun `returns null for blank`() {
        assertEquals(null, EmailNormalizer.normalize("   "))
    }
}

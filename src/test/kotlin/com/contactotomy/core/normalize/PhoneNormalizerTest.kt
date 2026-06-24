package com.contactotomy.core.normalize

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneNormalizerTest {
    private val normalizer = PhoneNormalizer(defaultRegion = "US")

    @Test
    fun `normalizes a US number to E164`() {
        assertEquals("+15125551234", normalizer.normalize("(512) 555-1234"))
    }

    @Test
    fun `keeps an already-international number`() {
        assertEquals("+442071234567", normalizer.normalize("+44 20 7123 4567"))
    }

    @Test
    fun `returns null for ungrokkable input`() {
        assertEquals(null, normalizer.normalize("not a phone"))
    }
}

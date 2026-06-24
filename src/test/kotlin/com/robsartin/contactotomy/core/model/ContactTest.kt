package com.robsartin.contactotomy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactTest {
    @Test
    fun `contact retains multi-value fields and source`() {
        val contact = Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Robert", middle = "A", family = "Sartin"),
            phones = listOf("+15125551234"),
            rawPhones = listOf("(512) 555-1234"),
            emails = listOf("rob@example.com"),
            rawVCard = "BEGIN:VCARD\nEND:VCARD",
        )

        assertEquals(Source.APPLE, contact.source)
        assertEquals("Robert", contact.name.given)
        assertEquals(listOf("+15125551234"), contact.phones)
        assertEquals(listOf("rob@example.com"), contact.emails)
    }
}

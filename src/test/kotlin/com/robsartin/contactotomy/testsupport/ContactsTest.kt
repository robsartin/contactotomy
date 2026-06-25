package com.robsartin.contactotomy.testsupport

import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactsTest {
    @Test
    fun `factory builds a contact with the given fields`() {
        val c = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), org = "Acme")
        assertEquals("a", c.id)
        assertEquals("Rob", c.name.given)
        assertEquals(listOf("+1"), c.phones)
        assertEquals(listOf("+1"), c.rawPhones)
        assertEquals("Acme", c.org)
        assertEquals(Source.APPLE, c.source)
    }
}

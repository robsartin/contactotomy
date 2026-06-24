package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcfExporterTest {
    @Test
    fun `writes vCard 3_0 with core fields`() {
        val contact = Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Robert", family = "Sartin", formatted = "Robert Sartin"),
            phones = listOf("+15125551234"),
            rawPhones = listOf("+15125551234"),
            emails = listOf("rob@example.com"),
            org = "Acme Inc.",
            categories = listOf("Work", "Friends"),
            rawVCard = "",
        )

        val text = VcfExporter().export(listOf(contact))

        assertTrue(text.contains("VERSION:3.0"))
        assertTrue(text.contains("FN:Robert Sartin"))
        assertTrue(text.contains("rob@example.com"))
        assertTrue(text.contains("+15125551234"))
        assertTrue(text.contains("Work") && text.contains("Friends"))
    }

    @Test
    fun `export then import round-trips core identity fields`() {
        val original = Contact(
            id = "1",
            source = Source.GOOGLE,
            name = ContactName(given = "Jane", middle = "Q", family = "Doe", formatted = "Jane Q Doe"),
            phones = listOf("+15125559999"),
            rawPhones = listOf("+15125559999"),
            emails = listOf("jane@example.com"),
            categories = listOf("Family"),
            rawVCard = "",
        )

        val text = VcfExporter().export(listOf(original))
        val reimported = com.robsartin.contactotomy.core.importer
            .VcfImporter(source = Source.GOOGLE).import(text).single()

        assertEquals("Jane", reimported.name.given)
        assertEquals("Doe", reimported.name.family)
        assertEquals(listOf("+15125559999"), reimported.phones)
        assertEquals(listOf("jane@example.com"), reimported.emails)
        assertEquals(listOf("Family"), reimported.categories)
    }
}

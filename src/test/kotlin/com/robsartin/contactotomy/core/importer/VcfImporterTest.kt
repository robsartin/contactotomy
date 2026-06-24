package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VcfImporterTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    @Test
    fun `imports cards with normalized fields and source tag`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))

        assertEquals(2, contacts.size)

        val rob = contacts.first()
        assertEquals(Source.APPLE, rob.source)
        assertEquals("Robert", rob.name.given)
        assertEquals("A", rob.name.middle)
        assertEquals("Sartin", rob.name.family)
        assertEquals(listOf("+15125551234"), rob.phones)
        assertEquals(listOf("(512) 555-1234"), rob.rawPhones)
        assertEquals(listOf("rob@example.com"), rob.emails)
        assertEquals("Acme Inc.", rob.org)
        assertEquals(listOf("Work", "Friends"), rob.categories)
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), rob.modifiedAt)
        assertTrue(rob.rawVCard.contains("BEGIN:VCARD"))
    }

    @Test
    fun `second card with only a formatted name parses cleanly`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))

        val noPhone = contacts[1]
        assertTrue(noPhone.phones.isEmpty())
        assertTrue(noPhone.rawPhones.isEmpty())
        assertTrue(noPhone.emails.isEmpty())
        assertEquals("No Phone Person", noPhone.name.formatted)
        assertNull(noPhone.name.given)
        assertNull(noPhone.name.family)
    }

    @Test
    fun `assigns stable distinct ids`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))
        assertEquals(contacts.size, contacts.map { it.id }.toSet().size)
    }
}

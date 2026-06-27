package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.importer.VcfImporter
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcfExporterAddressTest {
    private val exporter = VcfExporter()

    private fun contactWithAddress(vararg addresses: PostalAddress) =
        Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Test", family = "Person", formatted = "Test Person"),
            addresses = addresses.toList(),
            rawVCard = "",
        )

    @Test
    fun `exports PostalAddress as ADR with all non-null components`() {
        val contact =
            contactWithAddress(
                PostalAddress(
                    street = "123 Main St",
                    city = "Springfield",
                    region = "IL",
                    postalCode = "62701",
                    country = "USA",
                ),
            )

        val text = exporter.export(listOf(contact))

        assertTrue(text.contains("Springfield"), "Expected city 'Springfield' in: $text")
        assertTrue(text.contains("IL"), "Expected region 'IL' in: $text")
        assertTrue(text.contains("62701"), "Expected postal code '62701' in: $text")
        assertTrue(text.contains("USA"), "Expected country 'USA' in: $text")
        assertTrue(text.contains("123 Main St"), "Expected street '123 Main St' in: $text")
    }

    @Test
    fun `exports poBox and extended address components`() {
        val contact =
            contactWithAddress(
                PostalAddress(
                    poBox = "PO Box 1",
                    extended = "Suite 200",
                    street = "456 Oak Ave",
                    city = "Austin",
                    region = "TX",
                    postalCode = "78701",
                    country = "US",
                ),
            )

        val text = exporter.export(listOf(contact))

        assertTrue(text.contains("PO Box 1"), "Expected poBox in: $text")
        assertTrue(text.contains("Suite 200"), "Expected extended in: $text")
    }

    @Test
    fun `contact with no addresses exports no ADR line`() {
        val contact = contactWithAddress()
        val text = exporter.export(listOf(contact))
        assertTrue(!text.contains("ADR"), "Expected no ADR line in: $text")
    }

    @Test
    fun `round-trip import vCard with address, export, reimport preserves all components`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Round Trip Person
            ADR;TYPE=HOME:;;789 Elm Rd;Dallas;TX;75201;USA
            END:VCARD
            """.trimIndent()

        val importer = VcfImporter(source = Source.GOOGLE)
        val imported = importer.import(vcf).single()

        val exported = exporter.export(listOf(imported))
        val reimported = importer.import(exported).single()

        assertEquals(1, reimported.addresses.size)
        val addr = reimported.addresses.single()
        assertEquals("789 Elm Rd", addr.street)
        assertEquals("Dallas", addr.city)
        assertEquals("TX", addr.region)
        assertEquals("75201", addr.postalCode)
        assertEquals("USA", addr.country)
    }
}

package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class VcfImporterAddressTest {
    private val importer = VcfImporter(source = Source.APPLE)

    @Test
    fun `parses full ADR into PostalAddress with all components`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Test Person
            ADR;TYPE=HOME:;;123 Main St;Springfield;IL;62701;USA
            END:VCARD
            """.trimIndent()

        val contacts = importer.import(vcf)

        assertEquals(1, contacts.size)
        val addresses = contacts.single().addresses
        assertEquals(1, addresses.size)
        val addr = addresses.single()
        assertEquals("123 Main St", addr.street)
        assertEquals("Springfield", addr.city)
        assertEquals("IL", addr.region)
        assertEquals("62701", addr.postalCode)
        assertEquals("USA", addr.country)
    }

    @Test
    fun `maps all ADR components including poBox and extended`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Full Address Person
            ADR:PO Box 1;Apt 2;456 Oak Ave;Austin;TX;78701;US
            END:VCARD
            """.trimIndent()

        val contacts = importer.import(vcf)

        val addr = contacts.single().addresses.single()
        assertEquals("PO Box 1", addr.poBox)
        assertEquals("Apt 2", addr.extended)
        assertEquals("456 Oak Ave", addr.street)
        assertEquals("Austin", addr.city)
        assertEquals("TX", addr.region)
        assertEquals("78701", addr.postalCode)
        assertEquals("US", addr.country)
    }

    @Test
    fun `contact with no ADR has empty addresses list`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:No Address Person
            END:VCARD
            """.trimIndent()

        val contacts = importer.import(vcf)
        assertEquals(emptyList<PostalAddress>(), contacts.single().addresses)
    }

    @Test
    fun `imports multiple addresses per contact`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Multi Address
            ADR;TYPE=HOME:;;100 Home St;Portland;OR;97201;USA
            ADR;TYPE=WORK:;;200 Work Ave;Seattle;WA;98101;USA
            END:VCARD
            """.trimIndent()

        val contacts = importer.import(vcf)
        assertEquals(2, contacts.single().addresses.size)
    }
}

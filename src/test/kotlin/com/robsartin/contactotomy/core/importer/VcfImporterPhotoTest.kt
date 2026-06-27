package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VcfImporterPhotoTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name"))
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `imports embedded PHOTO into base64 field`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("photo-embedded.vcf"))
        val photo = contacts.single().photo
        assertNotNull(photo)
        assertTrue(photo.base64!!.isNotEmpty(), "base64 should be non-empty")
        assertNull(photo.url)
        assertEquals("image/jpeg", photo.contentType?.lowercase())
    }

    @Test
    fun `imports URL PHOTO into url field`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("photo-url.vcf"))
        val photo = contacts.single().photo
        assertNotNull(photo)
        assertEquals("https://example.com/bob.jpg", photo.url)
        assertNull(photo.base64)
    }

    @Test
    fun `vCard with no PHOTO yields null photo`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))
        contacts.forEach { assertNull(it.photo) }
    }

    @Test
    fun `unknown PHOTO type is imported with best-effort contentType`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Test;User;;;
            FN:User Test
            PHOTO;ENCODING=b;TYPE=BMP:Qk0=
            END:VCARD
            """.trimIndent()
        val contacts = VcfImporter(source = Source.APPLE).import(vcf)
        val photo = contacts.single().photo
        assertNotNull(photo)
        assertTrue(photo.base64!!.isNotEmpty())
        // contentType may be null or "image/bmp" — just verify no crash and photo populated
    }
}

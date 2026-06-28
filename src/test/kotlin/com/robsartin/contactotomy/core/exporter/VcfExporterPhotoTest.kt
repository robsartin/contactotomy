package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.importer.VcfImporter
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VcfExporterPhotoTest {
    private val exporter = VcfExporter()
    private val importer = VcfImporter(source = Source.APPLE)

    private fun contactWithPhoto(photo: ContactPhoto?) =
        Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Alice", family = "Smith"),
            rawVCard = "",
            photo = photo,
        )

    @Test
    fun `exports embedded photo as PHOTO property in vCard`() {
        val photo = ContactPhoto(base64 = "/9j/4A==", contentType = "image/jpeg")
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(text.contains("PHOTO"), "exported vCard should contain PHOTO line")
    }

    @Test
    fun `exports url photo as PHOTO URI property`() {
        val photo = ContactPhoto(url = "https://example.com/alice.jpg", contentType = "image/jpeg")
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(text.contains("PHOTO"), "exported vCard should contain PHOTO line")
        assertTrue(text.contains("example.com/alice.jpg"), "exported PHOTO should contain the URL")
    }

    @Test
    fun `contact with null photo exports no PHOTO property`() {
        val text = exporter.export(listOf(contactWithPhoto(null)))
        assertTrue(!text.contains("PHOTO"), "no PHOTO line expected when photo is null")
    }

    @Test
    fun `ContactPhoto with neither base64 nor url exports no PHOTO`() {
        val photo = ContactPhoto(contentType = "image/jpeg") // both base64 and url are null
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(!text.contains("PHOTO"), "no PHOTO line when neither base64 nor url is set")
    }

    @Test
    fun `round-trip embedded photo preserves base64 data`() {
        val originalBytes = "hello photo".toByteArray()
        val originalBase64 =
            java.util.Base64
                .getEncoder()
                .encodeToString(originalBytes)
        val photo = ContactPhoto(base64 = originalBase64, contentType = "image/jpeg")
        val exported = exporter.export(listOf(contactWithPhoto(photo)))
        val reimported = importer.import(exported).single()
        assertNotNull(reimported.photo)
        val reimportedBytes =
            java.util.Base64
                .getDecoder()
                .decode(reimported.photo!!.base64)
        assertTrue(originalBytes.contentEquals(reimportedBytes), "round-trip should preserve photo bytes")
    }

    @Test
    fun `unknown contentType defaults to JPEG on export`() {
        val photo = ContactPhoto(base64 = "/9j/4A==", contentType = "image/bmp")
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(text.contains("PHOTO"), "PHOTO line should still be written for unknown type")
    }
}

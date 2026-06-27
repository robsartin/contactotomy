package com.robsartin.contactotomy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactPhotoTest {
    @Test
    fun `ContactPhoto with base64 stores data and contentType`() {
        val photo = ContactPhoto(base64 = "abc123", contentType = "image/jpeg")
        assertEquals("abc123", photo.base64)
        assertEquals("image/jpeg", photo.contentType)
        assertNull(photo.url)
    }

    @Test
    fun `ContactPhoto with url stores url and contentType`() {
        val photo = ContactPhoto(url = "https://example.com/photo.png", contentType = "image/png")
        assertEquals("https://example.com/photo.png", photo.url)
        assertEquals("image/png", photo.contentType)
        assertNull(photo.base64)
    }

    @Test
    fun `ContactPhoto defaults all fields to null`() {
        val photo = ContactPhoto()
        assertNull(photo.base64)
        assertNull(photo.url)
        assertNull(photo.contentType)
    }
}

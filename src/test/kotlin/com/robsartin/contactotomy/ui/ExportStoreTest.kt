package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.importer.VcfImporter
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportStoreTest {
    @Test
    fun `vcard exports vCard 3_0 that round-trips`() {
        val store = ExportStore(listOf(contact("a", given = "Al", family = "Jones", emails = listOf("al@x.com"))))
        val text = store.vcard()
        assertTrue(text.contains("VERSION:3.0"))
        val reimported = VcfImporter(Source.APPLE).import(text)
        assertEquals(listOf("al@x.com"), reimported.single().emails)
    }

    @Test
    fun `contactCount reflects input and recordExported updates state`() {
        val store = ExportStore(listOf(contact("a"), contact("b")))
        assertEquals(2, store.state.value.contactCount)
        store.recordExported("/tmp/out.vcf")
        assertEquals("/tmp/out.vcf", store.state.value.exportedPath)
    }

    @Test
    fun `recordError sets the error`() {
        val store = ExportStore(emptyList())
        store.recordError("disk full")
        assertEquals("disk full", store.state.value.error)
    }
}

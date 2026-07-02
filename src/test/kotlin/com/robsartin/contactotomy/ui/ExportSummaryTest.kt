package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class ExportSummaryTest {
    @Test
    fun `all stages present - merges and deletions happened`() {
        val contacts = (1..10).map { contact("c$it") }
        val reviewed = (1..8).map { contact("r$it") }
        val final = (1..6).map { contact("f$it") }
        val state =
            AppState(
                contacts = contacts,
                reviewedContacts = reviewed,
                finalContacts = final,
            )
        val summary = exportSummary(state)
        assertEquals(10, summary.imported)
        assertEquals(2, summary.merged) // 10 - 8
        assertEquals(2, summary.removed) // reviewedContacts.size - finalContacts.size = 8 - 6
        assertEquals(6, summary.exporting)
    }

    @Test
    fun `no merges no deletions - deltas are zero`() {
        val contacts = (1..5).map { contact("c$it") }
        val state = AppState(contacts = contacts)
        val summary = exportSummary(state)
        assertEquals(5, summary.imported)
        assertEquals(0, summary.merged)
        assertEquals(0, summary.removed)
        assertEquals(5, summary.exporting)
    }

    @Test
    fun `finalContacts null - removed is zero exporting from reviewed`() {
        val contacts = (1..10).map { contact("c$it") }
        val reviewed = (1..8).map { contact("r$it") }
        val state =
            AppState(
                contacts = contacts,
                reviewedContacts = reviewed,
                finalContacts = null,
            )
        val summary = exportSummary(state)
        assertEquals(10, summary.imported)
        assertEquals(2, summary.merged)
        assertEquals(0, summary.removed)
        assertEquals(8, summary.exporting)
    }

    @Test
    fun `reviewedContacts null - merged delta is zero fallback for removed uses contacts`() {
        val contacts = (1..10).map { contact("c$it") }
        val final = (1..7).map { contact("f$it") }
        val state =
            AppState(
                contacts = contacts,
                reviewedContacts = null,
                finalContacts = final,
            )
        val summary = exportSummary(state)
        assertEquals(10, summary.imported)
        assertEquals(0, summary.merged)
        assertEquals(3, summary.removed) // contacts.size - finalContacts.size = 10 - 7
        assertEquals(7, summary.exporting)
    }
}

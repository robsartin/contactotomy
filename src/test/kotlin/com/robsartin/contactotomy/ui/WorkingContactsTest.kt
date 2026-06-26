package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkingContactsTest {
    private val imported = listOf(contact("i"))
    private val merged = listOf(contact("m"))
    private val finalSet = listOf(contact("f"))

    @Test
    fun `prefers final, then merged, then imported`() {
        assertEquals(
            listOf("f"),
            workingContacts(AppState(contacts = imported, mergedContacts = merged, finalContacts = finalSet)).map { it.id },
        )
        assertEquals(
            listOf("m"),
            workingContacts(AppState(contacts = imported, mergedContacts = merged)).map { it.id },
        )
        assertEquals(
            listOf("i"),
            workingContacts(AppState(contacts = imported)).map { it.id },
        )
    }
}

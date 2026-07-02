package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkingContactsTest {
    private val imported = listOf(contact("i"))
    private val reviewed = listOf(contact("r"))
    private val finalSet = listOf(contact("f"))

    @Test
    fun `prefers final, then reviewed, then imported`() {
        assertEquals(
            listOf("f"),
            workingContacts(AppState(contacts = imported, reviewedContacts = reviewed, finalContacts = finalSet)).map { it.id },
        )
        assertEquals(
            listOf("r"),
            workingContacts(AppState(contacts = imported, reviewedContacts = reviewed)).map { it.id },
        )
        assertEquals(
            listOf("i"),
            workingContacts(AppState(contacts = imported)).map { it.id },
        )
    }
}

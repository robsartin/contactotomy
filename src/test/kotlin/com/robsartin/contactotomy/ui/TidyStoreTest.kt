package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TidyStoreTest {
    private val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc")) // LEGAL_SUFFIX
    private val jane = contact("jane", given = "Jane", family = "Smith") // null -> not a suspect
    private val bottle = contact("bottle", given = "Blue", family = "Bottle") // null -> not a suspect

    @Test
    fun `high-precision suspects are pre-marked, others are not`() {
        val store = TidyStore(listOf(acme, jane))
        assertTrue("acme" in store.state.value.markedIds)
        assertFalse("jane" in store.state.value.markedIds)
    }

    @Test
    fun `toggle adds then removes a mark`() {
        val store = TidyStore(listOf(jane))
        store.toggle("jane")
        assertTrue("jane" in store.state.value.markedIds)
        store.toggle("jane")
        assertFalse("jane" in store.state.value.markedIds)
    }

    @Test
    fun `commit normalizes only marked contacts`() {
        val store = TidyStore(listOf(acme, jane))
        val result = store.commit().associateBy { it.id }
        assertEquals("Acme Inc", result["acme"]?.org)
        assertEquals(ContactName(), result["acme"]?.name)
        assertEquals("Jane", result["jane"]?.name?.given) // untouched
    }

    @Test
    fun `manually marking a detector-missed card normalizes it`() {
        val store = TidyStore(listOf(bottle))
        store.toggle("bottle")
        val out = store.commit().single()
        assertEquals("Blue Bottle", out.org)
        assertEquals(ContactName(), out.name)
    }

    @Test
    fun `listed omits nameless cards`() {
        val nameless = contact("x").copy(name = ContactName())
        val store = TidyStore(listOf(acme, nameless))
        assertEquals(listOf("acme"), store.listed().map { it.id })
    }

    @Test
    fun `email-name card is pre-marked and commit names it from the first email`() {
        val emailOnly = contact("e", emails = listOf("lonely@example.com")) // no name, no org
        val store = TidyStore(listOf(emailOnly))
        assertEquals(TidyAction.EMAIL_NAME, store.actionFor(emailOnly))
        assertTrue("e" in store.state.value.markedIds)
        val out = store.commit().single()
        assertEquals(ContactName(formatted = "lonely@example.com"), out.name)
    }

    @Test
    fun `a company-like name still maps to COMPANY and commits to org`() {
        val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc"))
        val store = TidyStore(listOf(acme))
        assertEquals(TidyAction.COMPANY, store.actionFor(acme))
        assertEquals("Acme Inc", store.commit().single().org)
    }

    @Test
    fun `listed includes a nameless email card and omits an empty card`() {
        val emailOnly = contact("e", emails = listOf("x@y.com"))
        val empty = contact("z") // no name, no email
        val store = TidyStore(listOf(emailOnly, empty))
        assertEquals(listOf("e"), store.listed().map { it.id })
    }
}

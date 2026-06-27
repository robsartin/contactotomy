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
}

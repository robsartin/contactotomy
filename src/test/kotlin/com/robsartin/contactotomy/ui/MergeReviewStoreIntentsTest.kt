package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreIntentsTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), org = "Acme Inc")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), org = "Acme")
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `reject changes a single item`() {
        val store = dupStore()
        val id =
            store.state.value.items
                .single()
                .id
        store.reject(id)
        assertEquals(
            Decision.REJECT,
            store.state.value.items
                .single()
                .decision,
        )
    }

    @Test
    fun `undo returns an item to PENDING`() {
        val store = dupStore()
        val id =
            store.state.value.items
                .single()
                .id
        store.accept(id)
        store.undo(id)
        assertEquals(
            Decision.PENDING,
            store.state.value.items
                .single()
                .decision,
        )
    }

    @Test
    fun `toggleField adds then removes an exclusion`() {
        val store = dupStore()
        val id =
            store.state.value.items
                .single()
                .id
        val ev = ExcludedValue("phones", "+15125551234")
        store.toggleField(id, ev)
        assertTrue(
            ev in
                store.state.value.items
                    .single()
                    .excludedValues,
        )
        store.toggleField(id, ev)
        assertTrue(
            ev !in
                store.state.value.items
                    .single()
                    .excludedValues,
        )
    }

    @Test
    fun `chooseConflict records a field choice`() {
        val store = dupStore()
        val id =
            store.state.value.items
                .single()
                .id
        store.chooseConflict(id, "org", "Acme")
        assertEquals(
            "Acme",
            store.state.value.items
                .single()
                .conflictChoices["org"],
        )
    }

    @Test
    fun `acceptAllHighConfidence accepts HIGH items only`() {
        val store = dupStore()
        val id =
            store.state.value.items
                .single()
                .id
        store.reject(id)
        store.acceptAllHighConfidence()
        assertEquals(
            Decision.ACCEPT,
            store.state.value.items
                .single()
                .decision,
        )
    }
}

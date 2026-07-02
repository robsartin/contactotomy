package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TDD tests for Task 1 of #81: `previewContact` is the single source of truth.
 * These tests drive:
 *  - previewContact returns the exact Contact commit() produces for an accepted item
 *  - works with exclusions + name/org overrides
 *  - no-override item preview equals proposal.merged shaped by decisions
 */
class MergeReviewStorePreviewContactTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), org = "Corp A")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), org = "Corp A")
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `previewContact equals the committed contact for a plain accepted item`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        val preview = store.previewContact(item)
        val committed = store.commit().single()
        assertEquals(committed.name, preview.name)
        assertEquals(committed.org, preview.org)
        assertEquals(committed.phones, preview.phones)
        assertEquals(committed.emails, preview.emails)
        assertEquals(committed.notes, preview.notes)
    }

    @Test
    fun `previewContact reflects a field exclusion before commit`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.toggleField(item.id, ExcludedValue("phones", "+15125551234"))
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        // Phone was excluded — preview must not contain it
        assert("+15125551234" !in preview.phones) { "Expected phone excluded from preview" }
        // Committed result must match preview
        val committed = store.commit().single()
        assertEquals(committed.phones, preview.phones)
    }

    @Test
    fun `previewContact reflects a name override`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNameComponent(item.id, NameComponent.GIVEN, "Roberta")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        assertEquals("Roberta", preview.name.given)
        // Commit must agree
        val committed = store.commit().single()
        assertEquals(committed.name, preview.name)
    }

    @Test
    fun `previewContact reflects an org override`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setOrgOverride(item.id, "New Corp")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        assertEquals("New Corp", preview.org)
        val committed = store.commit().single()
        assertEquals(committed.org, preview.org)
    }

    @Test
    fun `previewContact reflects a notes override`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNotesOverride(item.id, "My note")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        assertEquals("My note", preview.notes)
        val committed = store.commit().single()
        assertEquals(committed.notes, preview.notes)
    }

    @Test
    fun `previewContact on item with exclusion plus name and org override matches commit`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), org = "Corp A")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), org = "Corp A")
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.toggleField(item.id, ExcludedValue("phones", "+15125551234"))
        store.setNameComponent(item.id, NameComponent.GIVEN, "Bobby")
        store.setOrgOverride(item.id, "Bobby Corp")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        val committed = store.commit().single()
        assertEquals(committed.name, preview.name)
        assertEquals(committed.org, preview.org)
        assertEquals(committed.phones, preview.phones)
    }

    @Test
    fun `previewContact on no-override item equals proposal merged (name)`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        val preview = store.previewContact(item)
        // No overrides, no choice, so effective name = merged name
        assertEquals(item.proposal.merged.name, preview.name)
    }

    @Test
    fun `previewContact reflects added phones`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addPhone(item.id, "+18005550000")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        assert("+18005550000" in preview.phones) { "Added phone missing from preview" }
        val committed = store.commit().single()
        assertEquals(committed.phones, preview.phones)
    }

    @Test
    fun `previewContact reflects added emails`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addEmail(item.id, "new@example.com")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        assert("new@example.com" in preview.emails) { "Added email missing from preview" }
        val committed = store.commit().single()
        assertEquals(committed.emails, preview.emails)
    }

    @Test
    fun `previewContact org override blank yields null org`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setOrgOverride(item.id, "")
        val updatedItem =
            store.state.value.items
                .single()
        val preview = store.previewContact(updatedItem)
        assertNull(preview.org)
        val committed = store.commit().single()
        assertEquals(committed.org, preview.org)
    }
}

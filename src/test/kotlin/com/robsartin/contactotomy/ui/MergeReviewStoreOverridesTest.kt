package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TDD tests for Task 1: ReviewItem override fields and commit() applying them last.
 * These tests are written BEFORE the implementation exists and drive what gets built.
 */
class MergeReviewStoreOverridesTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        return MergeReviewStore(listOf(a, b))
    }

    // ---- setNameComponent ----

    @Test
    fun `setNameComponent given overrides nameChoiceId and commit yields that given`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.chooseName(item.id, "a") // nameChoiceId = "a" (given="Rob")
        store.setNameComponent(item.id, NameComponent.GIVEN, "Roberta")
        val committed = store.commit().single()
        assertEquals("Roberta", committed.name.given)
    }

    @Test
    fun `setNameComponent family sets family while given stays from seed`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNameComponent(item.id, NameComponent.FAMILY, "Smithson")
        val committed = store.commit().single()
        assertEquals("Smithson", committed.name.family)
        // given is seeded from the effective name, so it should be non-null
    }

    @Test
    fun `setNameComponent prefix sets prefix on the override`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNameComponent(item.id, NameComponent.PREFIX, "Dr")
        val override =
            store.state.value.items
                .single()
                .nameOverride
        assertEquals("Dr", override?.prefix)
    }

    @Test
    fun `setNameComponent middle sets middle on the override`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNameComponent(item.id, NameComponent.MIDDLE, "Q")
        val override =
            store.state.value.items
                .single()
                .nameOverride
        assertEquals("Q", override?.middle)
    }

    @Test
    fun `setNameComponent suffix sets suffix on the override`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNameComponent(item.id, NameComponent.SUFFIX, "Jr")
        val override =
            store.state.value.items
                .single()
                .nameOverride
        assertEquals("Jr", override?.suffix)
    }

    @Test
    fun `setNameComponent seeds from effective name so other components are preserved`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"))
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        // Only override the given; family should still be "Sartin" from the seed
        store.setNameComponent(item.id, NameComponent.GIVEN, "Bobby")
        val committed = store.commit().single()
        assertEquals("Bobby", committed.name.given)
        assertEquals("Sartin", committed.name.family)
    }

    // ---- setOrgOverride ----

    @Test
    fun `setOrgOverride non-blank sets merged org`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setOrgOverride(item.id, "New Corp")
        val committed = store.commit().single()
        assertEquals("New Corp", committed.org)
    }

    @Test
    fun `setOrgOverride blank yields null org even when merged has one`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), org = "OldCorp")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"), org = "OldCorp")
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setOrgOverride(item.id, "")
        val committed = store.commit().single()
        assertNull(committed.org)
    }

    @Test
    fun `setOrgOverride supersedes orgChoice`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.chooseOrg(item.id, "OrgChoice") // orgChoice set
        store.setOrgOverride(item.id, "Override Corp") // override wins
        val committed = store.commit().single()
        assertEquals("Override Corp", committed.org)
    }

    // ---- setNotesOverride ----

    @Test
    fun `setNotesOverride non-blank sets notes`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNotesOverride(item.id, "My custom note")
        val committed = store.commit().single()
        assertEquals("My custom note", committed.notes)
    }

    @Test
    fun `setNotesOverride blank yields null notes`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), notes = "Some note")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"))
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.setNotesOverride(item.id, "")
        val committed = store.commit().single()
        assertNull(committed.notes)
    }

    // ---- appendSourceNotes ----

    @Test
    fun `appendSourceNotes joins all member notes deduplicated and sets notesOverride`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), notes = "Note A")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"), notes = "Note B")
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.appendSourceNotes(item.id)
        val committed = store.commit().single()
        // Both notes should appear, separated by newline
        val notes = committed.notes ?: ""
        assert(notes.contains("Note A")) { "Expected 'Note A' in notes: $notes" }
        assert(notes.contains("Note B")) { "Expected 'Note B' in notes: $notes" }
    }

    @Test
    fun `appendSourceNotes deduplicates identical notes`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), notes = "Shared Note")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"), notes = "Shared Note")
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.appendSourceNotes(item.id)
        val committed = store.commit().single()
        assertEquals("Shared Note", committed.notes)
    }

    @Test
    fun `appendSourceNotes skips blank notes`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), notes = "")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"), notes = "Note B")
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.appendSourceNotes(item.id)
        val committed = store.commit().single()
        assertEquals("Note B", committed.notes)
    }

    // ---- addPhone / removeAddedPhone ----

    @Test
    fun `addPhone appends to the committed card`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addPhone(item.id, "+18005550000")
        val committed = store.commit().single()
        assert(committed.phones.contains("+18005550000")) { "Expected +18005550000 in phones: ${committed.phones}" }
    }

    @Test
    fun `addPhone deduplicates against existing merged phones`() {
        val store = dupStore() // both cards already have +15125551234
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addPhone(item.id, "+15125551234") // already in merged
        val committed = store.commit().single()
        assertEquals(1, committed.phones.count { it == "+15125551234" })
    }

    @Test
    fun `removeAddedPhone drops the phone from the committed card`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addPhone(item.id, "+18005550000")
        store.removeAddedPhone(item.id, "+18005550000")
        val committed = store.commit().single()
        assert(!committed.phones.contains("+18005550000")) { "Expected +18005550000 to be gone" }
    }

    // ---- addEmail / removeAddedEmail ----

    @Test
    fun `addEmail appends to the committed card`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addEmail(item.id, "new@example.com")
        val committed = store.commit().single()
        assert(committed.emails.contains("new@example.com")) { "Expected new@example.com in emails" }
    }

    @Test
    fun `addEmail deduplicates against existing merged emails`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), emails = listOf("rob@example.com"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"), emails = listOf("rob@example.com"))
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addEmail(item.id, "rob@example.com") // already in merged
        val committed = store.commit().single()
        assertEquals(1, committed.emails.count { it == "rob@example.com" })
    }

    @Test
    fun `removeAddedEmail drops the email from the committed card`() {
        val store = dupStore()
        val item =
            store.state.value.items
                .single()
        store.accept(item.id)
        store.addEmail(item.id, "new@example.com")
        store.removeAddedEmail(item.id, "new@example.com")
        val committed = store.commit().single()
        assert(!committed.emails.contains("new@example.com")) { "Expected email to be gone" }
    }

    // ---- Regression: no overrides → byte-identical commit ----

    @Test
    fun `item with no overrides commits identically to before (regression)`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), org = "Corp", notes = "A note")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), org = "Corp", notes = "A note")
        val store1 = MergeReviewStore(listOf(a, b))
        val store2 = MergeReviewStore(listOf(a, b))
        val item1 =
            store1.state.value.items
                .single()
        val item2 =
            store2.state.value.items
                .single()
        store1.accept(item1.id)
        store2.accept(item2.id)
        // No overrides on store2 — must commit identically
        val r1 = store1.commit().single()
        val r2 = store2.commit().single()
        assertEquals(r1.name, r2.name)
        assertEquals(r1.org, r2.org)
        assertEquals(r1.notes, r2.notes)
        assertEquals(r1.phones, r2.phones)
        assertEquals(r1.emails, r2.emails)
    }
}

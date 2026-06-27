package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for clearConflict / chooseConflict interaction, and commit applying cleared fields. */
class MergeReviewStoreConflictClearTest {
    /**
     * Two cards that share a phone (so they cluster HIGH) but differ in both notes and title.
     * Contacts are built directly because the factory lacks a `title` param.
     */
    private fun notesTitleStore(): Pair<MergeReviewStore, String> {
        val a =
            Contact(
                id = "a",
                source = Source.APPLE,
                name = ContactName(given = "Al", family = "Bee"),
                phones = listOf("+15125551234"),
                rawPhones = listOf("+15125551234"),
                notes = "N1",
                title = "Manager",
                rawVCard = "",
            )
        val b =
            Contact(
                id = "b",
                source = Source.APPLE,
                name = ContactName(given = "Al", family = "Bee"),
                phones = listOf("+15125551234"),
                rawPhones = listOf("+15125551234"),
                notes = "N2",
                title = "Director",
                rawVCard = "",
            )
        val store = MergeReviewStore(listOf(a, b))
        val id =
            store.state.value.items
                .single()
                .id
        return store to id
    }

    @Test
    fun `clearConflict adds field to clearedConflicts`() {
        val (store, id) = notesTitleStore()
        store.clearConflict(id, "notes")
        assertTrue(
            "notes" in
                store.state.value.items
                    .single()
                    .clearedConflicts,
        )
    }

    @Test
    fun `clearConflict does not affect other fields`() {
        val (store, id) = notesTitleStore()
        store.clearConflict(id, "notes")
        assertTrue(
            "title" !in
                store.state.value.items
                    .single()
                    .clearedConflicts,
        )
    }

    @Test
    fun `chooseConflict removes field from clearedConflicts`() {
        val (store, id) = notesTitleStore()
        store.clearConflict(id, "notes")
        store.chooseConflict(id, "notes", "N1")
        assertTrue(
            "notes" !in
                store.state.value.items
                    .single()
                    .clearedConflicts,
        )
        assertEquals(
            "N1",
            store.state.value.items
                .single()
                .conflictChoices["notes"],
        )
    }

    @Test
    fun `commit nulls notes when cleared, leaves title chosen normally`() {
        val (store, id) = notesTitleStore()
        store.clearConflict(id, "notes")
        store.chooseConflict(id, "title", "Director")
        store.accept(id)
        val result = store.commit().single()
        assertNull(result.notes)
        assertEquals("Director", result.title)
    }

    @Test
    fun `commit nulls title when cleared`() {
        val (store, id) = notesTitleStore()
        store.clearConflict(id, "title")
        store.chooseConflict(id, "notes", "N2")
        store.accept(id)
        val result = store.commit().single()
        assertNull(result.title)
        assertEquals("N2", result.notes)
    }

    @Test
    fun `commit nulls both notes and title when both cleared`() {
        val (store, id) = notesTitleStore()
        store.clearConflict(id, "notes")
        store.clearConflict(id, "title")
        store.accept(id)
        val result = store.commit().single()
        assertNull(result.notes)
        assertNull(result.title)
    }
}

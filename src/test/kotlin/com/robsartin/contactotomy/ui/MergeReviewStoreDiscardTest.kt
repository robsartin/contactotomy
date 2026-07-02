package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD tests for Task 2 of #81: discardItem drops all cluster members from commit() output.
 *
 * - discardItem marks the item as Decision.DISCARD and records member ids in discardedIds.
 * - commit() omits contacts whose id is in discardedIds (neither merged nor passed through).
 * - Discard differs from reject (reject keeps members separate; discard removes them entirely).
 * - Untouched flow commits identically (regression).
 */
class MergeReviewStoreDiscardTest {
    // a & b form a cluster (shared phone + name match); c & d are lone singletons.
    private fun store(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        return MergeReviewStore(listOf(a, b, c, d))
    }

    @Test
    fun `discardItem sets decision to DISCARD`() {
        val store = store()
        val id =
            store.state.value.items
                .single()
                .id
        store.discardItem(id)
        assertEquals(
            Decision.DISCARD,
            store.state.value.items
                .single()
                .decision,
        )
    }

    @Test
    fun `discardItem records member ids in discardedIds`() {
        val store = store()
        val id =
            store.state.value.items
                .single()
                .id
        store.discardItem(id)
        val discardedIds = store.state.value.discardedIds
        assertTrue("a" in discardedIds)
        assertTrue("b" in discardedIds)
    }

    @Test
    fun `discardItem on unknown id is a no-op`() {
        val store = store()
        val beforeItems = store.state.value.items
        store.discardItem("nonexistent")
        assertEquals(beforeItems, store.state.value.items)
        assertTrue(
            store.state.value.discardedIds
                .isEmpty(),
        )
    }

    @Test
    fun `commit after discardItem omits both member contacts from output`() {
        val store = store()
        val id =
            store.state.value.items
                .single()
                .id
        store.discardItem(id)
        val result = store.commit()
        // a and b are discarded; c and d remain; size drops from 4 to 2
        assertEquals(2, result.size)
        val ids = result.map { it.id }.toSet()
        assertFalse("a" in ids, "discarded member 'a' should not appear in output")
        assertFalse("b" in ids, "discarded member 'b' should not appear in output")
        assertTrue("c" in ids)
        assertTrue("d" in ids)
    }

    @Test
    fun `discard drops more contacts than reject (reject keeps members separate)`() {
        // Two stores, same contacts: one rejects the cluster, one discards it.
        val contacts =
            listOf(
                contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234")),
                contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234")),
            )
        val rejectStore = MergeReviewStore(contacts)
        val discardStore = MergeReviewStore(contacts)

        val rejectId =
            rejectStore.state.value.items
                .single()
                .id
        rejectStore.reject(rejectId)
        val rejectResult = rejectStore.commit()
        // reject: both a and b pass through unmerged
        assertEquals(2, rejectResult.size)

        val discardId =
            discardStore.state.value.items
                .single()
                .id
        discardStore.discardItem(discardId)
        val discardResult = discardStore.commit()
        // discard: both a and b are removed — output is empty
        assertEquals(0, discardResult.size)
    }

    @Test
    fun `discard differs from accept - accept collapses to one but discard removes both`() {
        val contacts =
            listOf(
                contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234")),
                contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234")),
            )
        val acceptStore = MergeReviewStore(contacts)
        val discardStore = MergeReviewStore(contacts)

        val acceptId =
            acceptStore.state.value.items
                .single()
                .id
        acceptStore.accept(acceptId)
        assertEquals(1, acceptStore.commit().size) // merged into one

        val discardId =
            discardStore.state.value.items
                .single()
                .id
        discardStore.discardItem(discardId)
        assertEquals(0, discardStore.commit().size) // both dropped
    }

    @Test
    fun `untouched cluster flow commits identically after discardItem on a different item`() {
        // Discard a & b; c and d were not in the cluster, so they commit through unchanged.
        val store = store()
        val id =
            store.state.value.items
                .single()
                .id
        store.discardItem(id)
        val result = store.commit()
        val ids = result.map { it.id }.toSet()
        // c and d are untouched — they pass through
        assertTrue("c" in ids)
        assertTrue("d" in ids)
    }

    @Test
    fun `discardItem is distinct from deleteItem — delete removes suggestion, discard persists it with DISCARD decision`() {
        val contacts =
            listOf(
                contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234")),
                contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234")),
            )
        // deleteItem removes the ReviewItem from the list
        val deleteStore = MergeReviewStore(contacts)
        val deleteId =
            deleteStore.state.value.items
                .single()
                .id
        deleteStore.deleteItem(deleteId)
        assertTrue(
            deleteStore.state.value.items
                .none { it.id == deleteId },
            "deleteItem removes the item",
        )

        // discardItem keeps the ReviewItem but marks it DISCARD
        val discardStore = MergeReviewStore(contacts)
        val discardId =
            discardStore.state.value.items
                .single()
                .id
        discardStore.discardItem(discardId)
        assertTrue(
            discardStore.state.value.items
                .any { it.id == discardId },
            "discardItem keeps the item",
        )
        assertEquals(
            Decision.DISCARD,
            discardStore.state.value.items
                .single()
                .decision,
        )
    }
}

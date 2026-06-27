package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeReviewStoreDeleteTest {
    // a & b cluster (shared phone + nickname); c & d are lone singletons.
    private fun store(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        return MergeReviewStore(listOf(a, b, c, d))
    }

    @Test
    fun `deleteItem removes the item from state items`() {
        val store = store()
        val id =
            store.state.value.items
                .single()
                .id
        store.deleteItem(id)
        assertTrue(
            store.state.value.items
                .none { it.id == id },
        )
    }

    @Test
    fun `deleteItem on a manual item returns its members to eligibleForManualMerge`() {
        val store = store()
        val manualId = store.manualMerge(listOf("c", "d"))!!
        // c and d are now claimed; eligible should be empty
        assertEquals(emptyList(), store.eligibleForManualMerge())
        store.deleteItem(manualId)
        val eligibleIds = store.eligibleForManualMerge().map { it.id }.toSet()
        assertEquals(setOf("c", "d"), eligibleIds)
    }

    @Test
    fun `commit after deleteItem does not merge the deleted item's cards`() {
        val store = store()
        val manualId = store.manualMerge(listOf("c", "d"))!!
        store.accept(manualId)
        store.deleteItem(manualId)
        val result = store.commit()
        // a & b still present (2 cards, HIGH cluster still pending => not merged)
        // c & d still present as individuals (not merged) => 4 total
        val ids = result.map { it.id }.toSet()
        assertTrue("c" in ids)
        assertTrue("d" in ids)
        assertFalse(ids.any { it.contains("manual") })
    }
}

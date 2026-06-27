package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeReviewStoreManualMergeTest {
    // a & b cluster (shared phone + nickname); c & d are lone singletons.
    private fun store(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        return MergeReviewStore(listOf(a, b, c, d))
    }

    @Test
    fun `eligible pool excludes cards already in a cluster`() {
        val ids = store().eligibleForManualMerge().map { it.id }.toSet()
        assertEquals(setOf("c", "d"), ids)
    }

    @Test
    fun `manualMerge appends a PENDING MANUAL item over the chosen members`() {
        val store = store()
        val id = store.manualMerge(listOf("c", "d"))
        val item =
            store.state.value.items
                .first { it.id == id }
        assertEquals(Origin.MANUAL, item.origin)
        assertEquals(Decision.PENDING, item.decision)
        assertEquals(
            setOf("c", "d"),
            item.proposal.cluster.members
                .map { it.id }
                .toSet(),
        )
    }

    @Test
    fun `manualMerge removes its members from the eligible pool`() {
        val store = store()
        store.manualMerge(listOf("c", "d"))
        assertEquals(emptyList(), store.eligibleForManualMerge())
    }

    @Test
    fun `manualMerge with one id is a no-op returning null`() {
        val store = store()
        val before = store.state.value.items.size
        assertEquals(null, store.manualMerge(listOf("c")))
        assertEquals(before, store.state.value.items.size)
    }

    @Test
    fun `manualMerge where only one id is eligible is a no-op returning null`() {
        val store = store()
        val before = store.state.value.items.size
        // ids not in the eligible pool are ignored (a is already clustered)
        assertEquals(null, store.manualMerge(listOf("c", "a")))
        assertEquals(before, store.state.value.items.size)
    }

    @Test
    fun `acceptAllHighConfidence does not sweep a manual item`() {
        val store = store()
        val manualId = store.manualMerge(listOf("c", "d"))!!
        store.acceptAllHighConfidence()
        val manual =
            store.state.value.items
                .first { it.id == manualId }
        assertEquals(Decision.PENDING, manual.decision)
        // the HIGH cluster (a,b) WAS swept to ACCEPT
        assertTrue(
            store.state.value.items
                .any { it.origin == Origin.HIGH && it.decision == Decision.ACCEPT },
        )
    }

    @Test
    fun `accepting then committing a manual item collapses its cards`() {
        val store = store()
        val id = store.manualMerge(listOf("c", "d"))!!
        store.accept(id)
        val result = store.commit()
        // a & b untouched (2), c & d collapse into 1 merged contact => 3 total.
        assertEquals(3, result.size)
        val ids = result.map { it.id }.toSet()
        assertFalse("c" in ids)
        assertFalse("d" in ids)
    }
}

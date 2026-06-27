package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val item = store.state.value.items.first { it.id == id }
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
    fun `manualMerge with fewer than two eligible ids is a no-op returning null`() {
        val store = store()
        val before = store.state.value.items.size
        assertEquals(null, store.manualMerge(listOf("c")))
        // ids not in the eligible pool are ignored (a & b are already clustered)
        assertEquals(null, store.manualMerge(listOf("c", "a")))
        assertEquals(before, store.state.value.items.size)
    }
}

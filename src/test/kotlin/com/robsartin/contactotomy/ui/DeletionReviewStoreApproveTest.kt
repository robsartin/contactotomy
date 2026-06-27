package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewStoreApproveTest {
    private fun ranStore(): DeletionReviewStore {
        val store =
            DeletionReviewStore(
                listOf(
                    contact("a", given = "Al", emails = listOf("no-reply@example.com")),
                    contact("b", given = "Bo", emails = listOf("noreply@example.com")),
                    contact("c"), // no name, no phone -> flagged by the "no name and no phone" rule
                ),
            )
        store.run()
        return store
    }

    @Test
    fun `approve and unapprove a flagged id`() {
        val store = ranStore()
        store.approve("a")
        assertTrue("a" in store.state.value.approvedIds)
        store.unapprove("a")
        assertTrue("a" !in store.state.value.approvedIds)
    }

    @Test
    fun `approving a non-flagged id is a no-op`() {
        val store = ranStore()
        store.approve("zzz")
        assertTrue(
            store.state.value.approvedIds
                .isEmpty(),
        )
    }

    @Test
    fun `approveAllForRule approves exactly that rule's matches`() {
        val store = ranStore()
        store.approveAllForRule("no-reply senders")
        assertEquals(setOf("a", "b"), store.state.value.approvedIds)
    }

    @Test
    fun `run clears prior approvals`() {
        val store = ranStore()
        store.approve("a")
        assertTrue("a" in store.state.value.approvedIds)
        store.run()
        assertTrue(
            store.state.value.approvedIds
                .isEmpty(),
        )
    }

    @Test
    fun `approveAllForRule with an unknown rule name is a no-op`() {
        val store = ranStore()
        store.approveAllForRule("does-not-exist")
        assertTrue(
            store.state.value.approvedIds
                .isEmpty(),
        )
    }

    @Test
    fun `approveAll then clearApprovals`() {
        val store = ranStore()
        store.approveAll()
        assertEquals(
            store.state.value.flagged
                .map { it.contact.id }
                .toSet(),
            store.state.value.approvedIds,
        )
        store.clearApprovals()
        assertTrue(
            store.state.value.approvedIds
                .isEmpty(),
        )
    }
}

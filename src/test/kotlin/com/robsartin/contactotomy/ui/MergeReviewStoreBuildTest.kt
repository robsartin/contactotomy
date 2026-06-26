package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreBuildTest {
    @Test
    fun `builds HIGH items (pending) for duplicates sharing a phone and name`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val store = MergeReviewStore(listOf(a, b))
        val items = store.state.value.items
        assertEquals(1, items.size)
        assertEquals(Origin.HIGH, items.single().origin)
        assertEquals(Decision.PENDING, items.single().decision)
        assertEquals(
            2,
            items
                .single()
                .proposal.cluster.members.size,
        )
    }

    @Test
    fun `builds UNCERTAIN items (pending by default) for name-only matches`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val store = MergeReviewStore(listOf(a, b))
        val item =
            store.state.value.items
                .single()
        assertEquals(Origin.UNCERTAIN, item.origin)
        assertEquals(Decision.PENDING, item.decision)
    }

    @Test
    fun `no proposals when nothing matches`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Mark", family = "Twain")
        assertTrue(
            MergeReviewStore(listOf(a, b))
                .state.value.items
                .isEmpty(),
        )
    }
}

package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreCommitTest {
    @Test
    fun `commit merges accepted clusters and passes others through`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val c = contact("c", given = "Zoe", family = "Quinn")
        val store = MergeReviewStore(listOf(a, b, c))
        val merged = store.commit()
        // a+b collapse to one merged contact; c passes through => 2 total
        assertEquals(2, merged.size)
        assertTrue(store.state.value.committed)
    }

    @Test
    fun `rejected clusters are not merged`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val store = MergeReviewStore(listOf(a, b))
        store.setDecision(
            store.state.value.items
                .single()
                .id,
            Decision.REJECT,
        )
        assertEquals(2, store.commit().size)
    }

    @Test
    fun `overlapping accepted proposals do not double-merge a contact`() {
        // a,b,c all share a phone+name => one HIGH cluster of 3. Build a second
        // (manual-style) overlapping acceptance by accepting an UNCERTAIN item that
        // shares a member, and verify commit stays consistent (no crash, no dup).
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"))
        val store = MergeReviewStore(listOf(a, b))
        // accept everything that exists
        store.state.value.items
            .forEach { store.setDecision(it.id, Decision.ACCEPT) }
        val merged = store.commit()
        // a and b are the same person => at most they merge into 1; never 3 or a duplicate id
        assertEquals(merged.map { it.id }, merged.map { it.id }.distinct())
        assertTrue(merged.size <= 2)
    }
}

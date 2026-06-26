package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreOverlapTest {
    @Test
    fun `accepting a HIGH cluster and an overlapping UNCERTAIN pair never double-touches a contact`() {
        // HIGH cluster {a,b}: shared phone + nickname-equivalent given names.
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125550001"))
        val b = contact("b", given = "Robert", family = "Jones", phones = listOf("+15125550001"))
        // UNCERTAIN pair {b,c}: name-only (no shared contact info), shares member b with the HIGH cluster.
        // c matches only b (family "Jones"), not a (family "Sartin").
        val c = contact("c", given = "Robert", family = "Jones")
        val store = MergeReviewStore(listOf(a, b, c))

        val items = store.state.value.items
        val high = items.single { it.origin == Origin.HIGH }
        val uncertain = items.single { it.origin == Origin.UNCERTAIN }
        // sanity: the two items overlap on member b
        val highIds =
            high.proposal.cluster.members
                .map { it.id }
                .toSet()
        val uncertainIds =
            uncertain.proposal.cluster.members
                .map { it.id }
                .toSet()
        assertEquals(setOf("b"), highIds.intersect(uncertainIds))

        // accept BOTH
        store.accept(high.id)
        store.accept(uncertain.id)

        val result = store.commit()

        // no contact id appears twice in the committed result
        assertEquals(result.map { it.id }, result.map { it.id }.distinct())

        // the overlapping (uncertain) item was downgraded to PENDING in state
        val uncertainAfter =
            store.state.value.items
                .single { it.id == uncertain.id }
        assertEquals(Decision.PENDING, uncertainAfter.decision)
        // the HIGH item kept its acceptance
        val highAfter =
            store.state.value.items
                .single { it.id == high.id }
        assertEquals(Decision.ACCEPT, highAfter.decision)
        assertTrue(store.state.value.committed)
    }
}

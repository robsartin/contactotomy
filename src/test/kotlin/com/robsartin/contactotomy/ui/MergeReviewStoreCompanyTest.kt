package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergeReviewStoreCompanyTest {
    // a & b cluster on shared phone + identical name; both have an org so no auto-suggest.
    private fun orgCluster(): MergeReviewStore {
        val a = contact("a", given = "Pat", family = "Lee", org = "Acme", phones = listOf("+15125559999"))
        val b = contact("b", given = "Pat", family = "Lee", org = "Acme", phones = listOf("+15125559999"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `chooseOrg sets orgChoice`() {
        val store = orgCluster()
        val id = store.state.value.items.single().id
        store.chooseOrg(id, "Foo Corp")
        assertEquals("Foo Corp", store.state.value.items.single().orgChoice)
    }

    @Test
    fun `orgChoice defaults to null`() {
        assertNull(orgCluster().state.value.items.single().orgChoice)
    }
}

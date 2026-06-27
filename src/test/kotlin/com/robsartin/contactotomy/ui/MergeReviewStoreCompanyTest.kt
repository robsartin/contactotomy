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
        val id =
            store.state.value.items
                .single()
                .id
        store.chooseOrg(id, "Foo Corp")
        assertEquals(
            "Foo Corp",
            store.state.value.items
                .single()
                .orgChoice,
        )
    }

    @Test
    fun `orgChoice defaults to null`() {
        assertNull(
            orgCluster()
                .state.value.items
                .single()
                .orgChoice,
        )
    }

    @Test
    fun `commit applies a chosen org to the merged contact`() {
        val store = orgCluster()
        val id =
            store.state.value.items
                .single()
                .id
        store.chooseOrg(id, "Acme Incorporated")
        store.accept(id)
        val result = store.commit()
        assertEquals(1, result.size)
        assertEquals("Acme Incorporated", result.single().org)
    }

    @Test
    fun `commit with an empty org choice clears the merged org`() {
        val store = orgCluster() // merged would otherwise be "Acme"
        val id =
            store.state.value.items
                .single()
                .id
        store.chooseOrg(id, "")
        store.accept(id)
        val result = store.commit()
        assertNull(result.single().org)
    }

    @Test
    fun `manualMerge auto-suggests org from a company name and person name from another card`() {
        val jane = contact("jane", given = "Jane", family = "Smith", emails = listOf("jane@acme.com"))
        val acme = contact("acme", given = "Acme", family = "Inc", emails = listOf("info@acme.com"))
        val store = MergeReviewStore(listOf(jane, acme))
        val id = store.manualMerge(listOf("jane", "acme"))!!
        val item =
            store.state.value.items
                .first { it.id == id }
        assertEquals("Acme Inc", item.orgChoice)
        assertEquals("jane", item.nameChoiceId)
    }

    @Test
    fun `auto-suggest does not fire when a source already has an org`() {
        val jane = contact("jane", given = "Jane", family = "Smith")
        val acme = contact("acme", given = "Acme", family = "Inc", org = "Acme Corporation")
        val store = MergeReviewStore(listOf(jane, acme))
        val id = store.manualMerge(listOf("jane", "acme"))!!
        val item =
            store.state.value.items
                .first { it.id == id }
        assertNull(item.orgChoice)
        assertNull(item.nameChoiceId)
    }

    @Test
    fun `auto-suggest fires on a WEAK all-caps name`() {
        val person = contact("p", given = "Jane", family = "Smith", emails = listOf("x@y.com"))
        val weak = contact("w", given = "MOM", emails = listOf("m@y.com")) // all-caps => WEAK, no org
        val store = MergeReviewStore(listOf(person, weak))
        val id = store.manualMerge(listOf("p", "w"))!!
        val item =
            store.state.value.items
                .first { it.id == id }
        assertEquals("MOM", item.orgChoice)
        assertEquals("p", item.nameChoiceId)
    }

    @Test
    fun `org override coexists with a notes conflict`() {
        val a = contact("a", given = "Pat", family = "Lee", org = "Acme", notes = "N1", phones = listOf("+15125559999"))
        val b = contact("b", given = "Pat", family = "Lee", org = "Acme Inc", notes = "N2", phones = listOf("+15125559999"))
        val store = MergeReviewStore(listOf(a, b))
        val id =
            store.state.value.items
                .single()
                .id
        store.chooseOrg(id, "Acme Inc")
        store.chooseConflict(id, "notes", "N2")
        store.accept(id)
        val result = store.commit()
        assertEquals("Acme Inc", result.single().org)
        assertEquals("N2", result.single().notes)
    }

    @Test
    fun `buildItems auto-suggests org for a clustered company card with no org`() {
        // identical company-like names + shared phone => one HIGH cluster
        val c1 = contact("c1", given = "Bobs", family = "Plumbing", phones = listOf("+15125550000"))
        val c2 = contact("c2", given = "Bobs", family = "Plumbing", phones = listOf("+15125550000"))
        val store = MergeReviewStore(listOf(c1, c2))
        assertEquals(
            "Bobs Plumbing",
            store.state.value.items
                .single()
                .orgChoice,
        )
        assertNull(
            store.state.value.items
                .single()
                .nameChoiceId,
        )
    }
}

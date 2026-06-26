package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewStoreRunTest {
    private fun store() =
        DeletionReviewStore(
            listOf(
                contact("a", given = "Al", emails = listOf("al@indeed.com")),
                contact("b", given = "Bo", emails = listOf("bo@personal.com")),
            ),
        )

    @Test
    fun `seeds rules from starter, all enabled`() {
        val s = store().state.value
        assertTrue(s.rules.isNotEmpty())
        assertTrue(s.rules.all { it.enabled })
        assertEquals(2, s.totalContacts)
        assertEquals(false, s.hasRun)
    }

    @Test
    fun `run flags via enabled rules only`() {
        val store = store()
        store.run()
        val s = store.state.value
        assertTrue(s.hasRun)
        // the starter "*@indeed.com" rule flags contact a
        assertEquals(listOf("a"), s.flagged.map { it.contact.id })
    }

    @Test
    fun `disabling the matching rule yields no flags`() {
        val store = store()
        // disable every rule, then run
        store.state.value.rules
            .forEach { store.toggleRule(it.rule.name) }
        store.run()
        assertTrue(
            store.state.value.flagged
                .isEmpty(),
        )
    }
}

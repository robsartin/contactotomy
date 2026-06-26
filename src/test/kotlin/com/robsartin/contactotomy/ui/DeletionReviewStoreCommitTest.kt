package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.RuleStore
import com.robsartin.contactotomy.core.rules.starter
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewStoreCommitTest {
    private fun store() =
        DeletionReviewStore(
            listOf(
                contact("a", given = "Al", emails = listOf("al@indeed.com")),
                contact("b", given = "Bo", emails = listOf("bo@personal.com")),
            ),
        )

    @Test
    fun `commit removes approved contacts and keeps the rest`() {
        val store = store()
        store.run()
        store.approve("a")
        val result = store.commit()
        assertEquals(listOf("b"), result.map { it.id })
        assertTrue(store.state.value.committed)
    }

    @Test
    fun `rulesToJson round-trips via RuleStore`() {
        val store = store()
        val parsed = RuleStore.fromJson(store.rulesToJson())
        assertEquals(RuleSet.starter().rules.map { it.name }, parsed.rules.map { it.name })
    }

    @Test
    fun `loadRules replaces rules and clears prior results`() {
        val store = store()
        store.run()
        store.approve("a")
        val oneRuleJson = RuleStore.toJson(RuleSet(RuleSet.starter().rules.take(1)))
        store.loadRules(oneRuleJson)
        val s = store.state.value
        assertEquals(1, s.rules.size)
        assertTrue(s.flagged.isEmpty())
        assertTrue(s.approvedIds.isEmpty())
        assertEquals(false, s.hasRun)
    }
}

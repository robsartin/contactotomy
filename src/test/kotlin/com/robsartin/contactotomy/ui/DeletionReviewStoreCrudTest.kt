package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.RuleStore
import com.robsartin.contactotomy.core.rules.TextField
import com.robsartin.contactotomy.core.rules.TextMatch
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeletionReviewStoreCrudTest {
    private val spam = Rule("spam rule", TextMatch(TextField.EMAIL, "*@spam.com"))
    private val noEmail =
        Rule(
            "no email",
            com.robsartin.contactotomy.core.rules
                .Predicate(com.robsartin.contactotomy.core.rules.PredicateKind.NO_EMAIL),
        )

    private val contacts =
        listOf(
            contact("a", given = "Alice", emails = listOf("alice@spam.com")),
            contact("b", given = "Bob", emails = listOf("bob@legit.com")),
            contact("c", given = "Carol"), // no email
        )

    private fun store() = DeletionReviewStore(contacts, initialRules = RuleSet(emptyList()))

    // ─── addRule ─────────────────────────────────────────────────────────────

    @Test
    fun `addRule appends with enabled true`() {
        val store = store()
        store.addRule(spam)
        val rules = store.state.value.rules
        assertEquals(1, rules.size)
        assertEquals("spam rule", rules.first().rule.name)
        assertTrue(rules.first().enabled)
    }

    @Test
    fun `addRule appends after existing rules`() {
        val store = store()
        store.addRule(spam)
        store.addRule(noEmail)
        val rules = store.state.value.rules
        assertEquals(2, rules.size)
        assertEquals("spam rule", rules[0].rule.name)
        assertEquals("no email", rules[1].rule.name)
    }

    @Test
    fun `added rule participates in run`() {
        val store = store()
        store.addRule(spam)
        store.run()
        val flaggedIds =
            store.state.value.flagged
                .map { it.contact.id }
        assertEquals(listOf("a"), flaggedIds)
    }

    // ─── updateRule ──────────────────────────────────────────────────────────

    @Test
    fun `updateRule replaces rule by original name and preserves list position`() {
        val store = store()
        store.addRule(spam)
        store.addRule(noEmail)
        val updated = Rule("spam rule v2", TextMatch(TextField.EMAIL, "*@updated.com"))
        store.updateRule("spam rule", updated)
        val rules = store.state.value.rules
        assertEquals(2, rules.size)
        assertEquals("spam rule v2", rules[0].rule.name)
        assertEquals("no email", rules[1].rule.name)
    }

    @Test
    fun `updateRule preserves enabled flag`() {
        val store = store()
        store.addRule(spam)
        store.toggleRule("spam rule")
        assertFalse(
            store.state.value.rules
                .first()
                .enabled,
        )
        val updated = Rule("spam rule", TextMatch(TextField.EMAIL, "*@updated.com"))
        store.updateRule("spam rule", updated)
        assertFalse(
            store.state.value.rules
                .first()
                .enabled,
        ) // preserved
    }

    @Test
    fun `updateRule with same name keeps it in place`() {
        val store = store()
        store.addRule(spam)
        val updated = Rule("spam rule", TextMatch(TextField.EMAIL, "*@newdomain.com"))
        store.updateRule("spam rule", updated)
        val rules = store.state.value.rules
        assertEquals(1, rules.size)
        assertEquals("*@newdomain.com", (rules.first().rule.condition as TextMatch).glob)
    }

    // ─── removeRule ──────────────────────────────────────────────────────────

    @Test
    fun `removeRule drops the rule by name`() {
        val store = store()
        store.addRule(spam)
        store.addRule(noEmail)
        store.removeRule("spam rule")
        val rules = store.state.value.rules
        assertEquals(1, rules.size)
        assertEquals("no email", rules.first().rule.name)
    }

    @Test
    fun `removeRule on unknown name is a no-op`() {
        val store = store()
        store.addRule(spam)
        store.removeRule("nonexistent")
        assertEquals(1, store.state.value.rules.size)
    }

    // ─── rulesToJson round-trip ───────────────────────────────────────────────

    @Test
    fun `rulesToJson round-trips an added rule via RuleStore`() {
        val store = store()
        store.addRule(spam)
        val json = store.rulesToJson()
        val parsed = RuleStore.fromJson(json)
        assertEquals(1, parsed.rules.size)
        assertEquals("spam rule", parsed.rules.first().name)
        assertEquals(TextMatch(TextField.EMAIL, "*@spam.com"), parsed.rules.first().condition)
    }
}

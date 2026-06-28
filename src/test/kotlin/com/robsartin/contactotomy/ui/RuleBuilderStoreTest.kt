package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.rules.And
import com.robsartin.contactotomy.core.rules.PhoneMatch
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.core.rules.TextField
import com.robsartin.contactotomy.core.rules.TextMatch
import com.robsartin.contactotomy.testsupport.contact
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleBuilderStoreTest {
    private val emptyContacts = emptyList<com.robsartin.contactotomy.core.model.Contact>()

    // ─── New store defaults ──────────────────────────────────────────────────

    @Test
    fun `new store has LeafText root and is invalid`() {
        val store = RuleBuilderStore(emptyContacts)
        val state = store.state.value
        assertTrue(state.root is LeafText)
        assertFalse(store.isValid)
    }

    @Test
    fun `new store matchCount is null`() {
        val store = RuleBuilderStore(emptyContacts)
        assertNull(store.matchCount())
    }

    @Test
    fun `new store name is blank`() {
        val store = RuleBuilderStore(emptyContacts)
        assertTrue(
            store.state.value.name
                .isBlank(),
        )
    }

    // ─── setName ─────────────────────────────────────────────────────────────

    @Test
    fun `setName updates the name`() {
        val store = RuleBuilderStore(emptyContacts)
        store.setName("My Rule")
        assertEquals("My Rule", store.state.value.name)
    }

    // ─── setGlob ────────────────────────────────────────────────────────────

    @Test
    fun `setGlob on root LeafText updates glob and makes name-set store valid`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.setGlob(rootId, "*@x.com")
        assertFalse(store.isValid) // still invalid: name blank
        store.setName("test")
        assertTrue(store.isValid)
    }

    @Test
    fun `blank name means invalid even with valid glob`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.setGlob(rootId, "*@x.com")
        store.setName("")
        assertFalse(store.isValid)
    }

    @Test
    fun `toRuleOrNull returns null until both name and glob are set`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        assertNull(store.toRuleOrNull())
        store.setGlob(rootId, "*@x.com")
        assertNull(store.toRuleOrNull()) // name still blank
        store.setName("r")
        val rule = store.toRuleOrNull()
        assertNotNull(rule)
        assertEquals("r", rule.name)
        assertEquals(TextMatch(TextField.EMAIL, "*@x.com"), rule.condition)
    }

    // ─── setField ────────────────────────────────────────────────────────────

    @Test
    fun `setField changes the text field on a LeafText`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.setField(rootId, TextField.NAME)
        val root = store.state.value.root as LeafText
        assertEquals(TextField.NAME, root.field)
    }

    // ─── changeKind ──────────────────────────────────────────────────────────

    @Test
    fun `changeKind TEXT to AND converts leaf to empty BranchAnd`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.AND)
        assertTrue(store.state.value.root is BranchAnd)
        assertEquals(emptyList(), (store.state.value.root as BranchAnd).children)
    }

    @Test
    fun `changeKind TEXT to OR converts leaf to empty BranchOr`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.OR)
        assertTrue(store.state.value.root is BranchOr)
    }

    @Test
    fun `changeKind TEXT to NOT converts leaf to BranchNot with null child`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.NOT)
        val root = store.state.value.root as BranchNot
        assertNull(root.child)
    }

    @Test
    fun `changeKind TEXT to PHONE converts to LeafPhone`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.PHONE)
        assertTrue(store.state.value.root is LeafPhone)
    }

    @Test
    fun `changeKind TEXT to PREDICATE converts to LeafPredicate`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.PREDICATE)
        assertTrue(store.state.value.root is LeafPredicate)
    }

    @Test
    fun `changeKind AND to OR preserves children`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.AND)
        store.addChild(rootId)
        val andState = store.state.value.root as BranchAnd
        val childId = andState.children.first().id
        store.changeKind(rootId, NodeKind.OR)
        val or = store.state.value.root as BranchOr
        assertEquals(1, or.children.size)
        assertEquals(childId, or.children.first().id)
    }

    @Test
    fun `changeKind OR to AND preserves children`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.OR)
        store.addChild(rootId)
        store.changeKind(rootId, NodeKind.AND)
        assertTrue(store.state.value.root is BranchAnd)
        assertEquals(1, (store.state.value.root as BranchAnd).children.size)
    }

    @Test
    fun `changeKind AND to NOT keeps first child`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.AND)
        store.addChild(rootId) // adds first child
        store.addChild(rootId) // adds second child
        val andState = store.state.value.root as BranchAnd
        val firstChildId = andState.children.first().id
        store.changeKind(rootId, NodeKind.NOT)
        val not = store.state.value.root as BranchNot
        assertEquals(firstChildId, not.child?.id)
    }

    @Test
    fun `changeKind branch to leaf drops children`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.AND)
        store.addChild(rootId)
        store.changeKind(rootId, NodeKind.TEXT) // leaf: drop children
        assertTrue(store.state.value.root is LeafText)
    }

    // ─── addChild ────────────────────────────────────────────────────────────

    @Test
    fun `addChild to AND root adds default LeafText child`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.AND)
        store.addChild(rootId)
        val branch = store.state.value.root as BranchAnd
        assertEquals(1, branch.children.size)
        assertTrue(branch.children.first() is LeafText)
    }

    // ─── removeNode ──────────────────────────────────────────────────────────

    @Test
    fun `removeNode on child removes it from parent`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.AND)
        store.addChild(rootId)
        val childId = (store.state.value.root as BranchAnd).children.first().id
        store.removeNode(childId)
        val branch = store.state.value.root as BranchAnd
        assertTrue(branch.children.isEmpty())
    }

    @Test
    fun `removeNode on root is a no-op`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.removeNode(rootId)
        assertEquals(rootId, store.state.value.root.id) // root unchanged
    }

    // ─── setPattern ──────────────────────────────────────────────────────────

    @Test
    fun `setPattern on LeafPhone updates pattern`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.PHONE)
        store.setPattern(rootId, "+1*")
        val root = store.state.value.root as LeafPhone
        assertEquals("+1*", root.pattern)
    }

    // ─── setPredicateKind ────────────────────────────────────────────────────

    @Test
    fun `setPredicateKind changes the predicate kind`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.PREDICATE)
        store.setPredicateKind(rootId, PredicateKind.NO_PHONE)
        val root = store.state.value.root as LeafPredicate
        assertEquals(PredicateKind.NO_PHONE, root.kind)
    }

    // ─── setBefore ───────────────────────────────────────────────────────────

    @Test
    fun `setBefore sets the date on LeafPredicate`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.PREDICATE)
        store.setPredicateKind(rootId, PredicateKind.CREATED_BEFORE)
        val instant = Instant.parse("2023-01-01T00:00:00Z")
        store.setBefore(rootId, instant)
        val root = store.state.value.root as LeafPredicate
        assertEquals(instant, root.before)
    }

    // ─── setSource ───────────────────────────────────────────────────────────

    @Test
    fun `setSource sets the source on LeafPredicate`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.changeKind(rootId, NodeKind.PREDICATE)
        store.setPredicateKind(rootId, PredicateKind.SOURCE_IS)
        store.setSource(rootId, Source.GOOGLE)
        val root = store.state.value.root as LeafPredicate
        assertEquals(Source.GOOGLE, root.source)
    }

    // ─── matchCount ──────────────────────────────────────────────────────────

    @Test
    fun `matchCount returns correct count against fixture contacts`() {
        val contacts =
            listOf(
                contact("a", given = "Alice", emails = listOf("alice@spam.com")),
                contact("b", given = "Bob", emails = listOf("bob@legit.com")),
                contact("c", given = "Carol", emails = listOf("carol@spam.com")),
            )
        val store = RuleBuilderStore(contacts)
        val rootId = store.state.value.root.id
        store.setName("spam rule")
        store.setGlob(rootId, "*@spam.com")
        // 2 contacts match *@spam.com
        assertEquals(2, store.matchCount())
    }

    @Test
    fun `matchCount null when condition invalid`() {
        val store = RuleBuilderStore(listOf(contact("a", given = "Alice")))
        assertNull(store.matchCount())
    }

    // ─── Edit construction ───────────────────────────────────────────────────

    @Test
    fun `edit construction with existing Rule populates name and root`() {
        val existing =
            Rule(
                "my-rule",
                And(
                    of =
                        listOf(
                            TextMatch(TextField.EMAIL, "*@x.com"),
                            PhoneMatch("+1*"),
                        ),
                ),
            )
        val store = RuleBuilderStore(emptyContacts, existing = existing)
        assertEquals("my-rule", store.state.value.name)
        val root = store.state.value.root
        assertTrue(root is BranchAnd)
        assertEquals(2, (root as BranchAnd).children.size)
        // round-trip check
        assertEquals(existing.condition, root.toConditionOrNull())
    }

    @Test
    fun `edit construction is valid when existing rule is valid`() {
        val existing = Rule("r", TextMatch(TextField.EMAIL, "*@x.com"))
        val store = RuleBuilderStore(emptyContacts, existing = existing)
        assertTrue(store.isValid)
    }

    @Test
    fun `isValid false for Predicate CREATED_BEFORE without date`() {
        val store = RuleBuilderStore(emptyContacts)
        val rootId = store.state.value.root.id
        store.setName("r")
        store.changeKind(rootId, NodeKind.PREDICATE)
        store.setPredicateKind(rootId, PredicateKind.CREATED_BEFORE)
        assertFalse(store.isValid)
        store.setBefore(rootId, Instant.parse("2024-01-01T00:00:00Z"))
        assertTrue(store.isValid)
    }

    @Test
    fun `matchCount with Predicate NO_EMAIL flags contacts without email`() {
        val contacts =
            listOf(
                contact("a", given = "Alice", emails = listOf("alice@x.com")),
                contact("b", given = "Bob"), // no email
            )
        val store = RuleBuilderStore(contacts)
        val rootId = store.state.value.root.id
        store.setName("no email")
        store.changeKind(rootId, NodeKind.PREDICATE)
        store.setPredicateKind(rootId, PredicateKind.NO_EMAIL)
        assertEquals(1, store.matchCount())
    }
}

package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.rules.And
import com.robsartin.contactotomy.core.rules.Not
import com.robsartin.contactotomy.core.rules.Or
import com.robsartin.contactotomy.core.rules.PhoneMatch
import com.robsartin.contactotomy.core.rules.Predicate
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.TextField
import com.robsartin.contactotomy.core.rules.TextMatch
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RuleBuilderModelTest {
    private var idCounter = 0

    private fun nextId() = "n${idCounter++}"

    // ─── LeafText ───────────────────────────────────────────────────────────

    @Test
    fun `LeafText with blank glob yields null`() {
        assertNull(LeafText("x", TextField.EMAIL, "").toConditionOrNull())
        assertNull(LeafText("x", TextField.EMAIL, "   ").toConditionOrNull())
    }

    @Test
    fun `LeafText with non-blank glob yields TextMatch`() {
        val result = LeafText("x", TextField.EMAIL, "*@x.com").toConditionOrNull()
        assertEquals(TextMatch(TextField.EMAIL, "*@x.com"), result)
    }

    @Test
    fun `LeafText name field round-trips`() {
        val result = LeafText("x", TextField.NAME, "Alice").toConditionOrNull()
        assertEquals(TextMatch(TextField.NAME, "Alice"), result)
    }

    // ─── LeafPhone ──────────────────────────────────────────────────────────

    @Test
    fun `LeafPhone with blank pattern yields null`() {
        assertNull(LeafPhone("x", "").toConditionOrNull())
        assertNull(LeafPhone("x", "  ").toConditionOrNull())
    }

    @Test
    fun `LeafPhone with non-blank pattern yields PhoneMatch`() {
        val result = LeafPhone("x", "+1*").toConditionOrNull()
        assertEquals(PhoneMatch("+1*"), result)
    }

    // ─── LeafPredicate ──────────────────────────────────────────────────────

    @Test
    fun `LeafPredicate NO_EMAIL yields Predicate`() {
        val result = LeafPredicate("x", PredicateKind.NO_EMAIL).toConditionOrNull()
        assertEquals(Predicate(PredicateKind.NO_EMAIL), result)
    }

    @Test
    fun `LeafPredicate NO_PHONE yields Predicate`() {
        val result = LeafPredicate("x", PredicateKind.NO_PHONE).toConditionOrNull()
        assertEquals(Predicate(PredicateKind.NO_PHONE), result)
    }

    @Test
    fun `LeafPredicate EMPTY_CARD yields Predicate`() {
        val result = LeafPredicate("x", PredicateKind.EMPTY_CARD).toConditionOrNull()
        assertEquals(Predicate(PredicateKind.EMPTY_CARD), result)
    }

    @Test
    fun `LeafPredicate NO_NAME_AND_NO_PHONE yields Predicate`() {
        val result = LeafPredicate("x", PredicateKind.NO_NAME_AND_NO_PHONE).toConditionOrNull()
        assertEquals(Predicate(PredicateKind.NO_NAME_AND_NO_PHONE), result)
    }

    @Test
    fun `LeafPredicate CREATED_BEFORE without date yields null`() {
        assertNull(LeafPredicate("x", PredicateKind.CREATED_BEFORE, before = null).toConditionOrNull())
    }

    @Test
    fun `LeafPredicate CREATED_BEFORE with date yields Predicate`() {
        val instant = Instant.parse("2024-01-01T00:00:00Z")
        val result = LeafPredicate("x", PredicateKind.CREATED_BEFORE, before = instant).toConditionOrNull()
        assertEquals(Predicate(PredicateKind.CREATED_BEFORE, before = instant), result)
    }

    @Test
    fun `LeafPredicate SOURCE_IS without source yields null`() {
        assertNull(LeafPredicate("x", PredicateKind.SOURCE_IS, source = null).toConditionOrNull())
    }

    @Test
    fun `LeafPredicate SOURCE_IS with source yields Predicate`() {
        val result = LeafPredicate("x", PredicateKind.SOURCE_IS, source = Source.GOOGLE).toConditionOrNull()
        assertEquals(Predicate(PredicateKind.SOURCE_IS, source = Source.GOOGLE), result)
    }

    // ─── BranchAnd ──────────────────────────────────────────────────────────

    @Test
    fun `BranchAnd empty yields null`() {
        assertNull(BranchAnd("x", emptyList()).toConditionOrNull())
    }

    @Test
    fun `BranchAnd with one invalid child yields null`() {
        val invalid = LeafText("c", TextField.EMAIL, "")
        assertNull(BranchAnd("x", listOf(invalid)).toConditionOrNull())
    }

    @Test
    fun `BranchAnd with one valid child yields And`() {
        val valid = LeafText("c", TextField.EMAIL, "*@x.com")
        val result = BranchAnd("x", listOf(valid)).toConditionOrNull()
        assertEquals(And(listOf(TextMatch(TextField.EMAIL, "*@x.com"))), result)
    }

    @Test
    fun `BranchAnd with mixed valid and invalid children yields null`() {
        val valid = LeafText("c1", TextField.EMAIL, "*@x.com")
        val invalid = LeafText("c2", TextField.EMAIL, "")
        assertNull(BranchAnd("x", listOf(valid, invalid)).toConditionOrNull())
    }

    @Test
    fun `BranchAnd with all valid children yields And`() {
        val c1 = LeafText("c1", TextField.EMAIL, "*@x.com")
        val c2 = LeafPhone("c2", "+1*")
        val result = BranchAnd("x", listOf(c1, c2)).toConditionOrNull()
        assertEquals(And(listOf(TextMatch(TextField.EMAIL, "*@x.com"), PhoneMatch("+1*"))), result)
    }

    // ─── BranchOr ───────────────────────────────────────────────────────────

    @Test
    fun `BranchOr empty yields null`() {
        assertNull(BranchOr("x", emptyList()).toConditionOrNull())
    }

    @Test
    fun `BranchOr with one invalid child yields null`() {
        val invalid = LeafPhone("c", "")
        assertNull(BranchOr("x", listOf(invalid)).toConditionOrNull())
    }

    @Test
    fun `BranchOr with all valid children yields Or`() {
        val c1 = LeafText("c1", TextField.NAME, "Alice")
        val c2 = LeafPhone("c2", "+44*")
        val result = BranchOr("x", listOf(c1, c2)).toConditionOrNull()
        assertEquals(Or(listOf(TextMatch(TextField.NAME, "Alice"), PhoneMatch("+44*"))), result)
    }

    // ─── BranchNot ──────────────────────────────────────────────────────────

    @Test
    fun `BranchNot with null child yields null`() {
        assertNull(BranchNot("x", null).toConditionOrNull())
    }

    @Test
    fun `BranchNot with invalid child yields null`() {
        val invalid = LeafText("c", TextField.EMAIL, "")
        assertNull(BranchNot("x", invalid).toConditionOrNull())
    }

    @Test
    fun `BranchNot with valid child yields Not`() {
        val valid = LeafPredicate("c", PredicateKind.NO_EMAIL)
        val result = BranchNot("x", valid).toConditionOrNull()
        assertEquals(Not(Predicate(PredicateKind.NO_EMAIL)), result)
    }

    // ─── replace ────────────────────────────────────────────────────────────

    @Test
    fun `replace on matching leaf replaces it`() {
        val leaf = LeafText("a", TextField.EMAIL, "old")
        val result = leaf.replace("a") { LeafText("a", TextField.EMAIL, "new") }
        assertEquals(LeafText("a", TextField.EMAIL, "new"), result)
    }

    @Test
    fun `replace on non-matching leaf is a no-op`() {
        val leaf = LeafText("a", TextField.EMAIL, "old")
        val result = leaf.replace("b") { LeafText("b", TextField.EMAIL, "new") }
        assertEquals(leaf, result)
    }

    @Test
    fun `replace in BranchAnd child updates only the matching child`() {
        val c1 = LeafText("c1", TextField.EMAIL, "original")
        val c2 = LeafPhone("c2", "+1*")
        val branch = BranchAnd("root", listOf(c1, c2))
        val result = branch.replace("c1") { LeafText("c1", TextField.EMAIL, "updated") }
        val expected = BranchAnd("root", listOf(LeafText("c1", TextField.EMAIL, "updated"), c2))
        assertEquals(expected, result)
    }

    @Test
    fun `replace in BranchNot child updates it`() {
        val child = LeafPredicate("child", PredicateKind.NO_EMAIL)
        val branch = BranchNot("root", child)
        val updated = LeafPredicate("child", PredicateKind.NO_PHONE)
        val result = branch.replace("child") { updated }
        assertEquals(BranchNot("root", updated), result)
    }

    @Test
    fun `replace in nested branch updates the deeply nested node`() {
        val inner = LeafText("inner", TextField.EMAIL, "old")
        val child = BranchAnd("mid", listOf(inner))
        val root = BranchOr("root", listOf(child))
        val result = root.replace("inner") { LeafText("inner", TextField.EMAIL, "new") }
        val expected = BranchOr("root", listOf(BranchAnd("mid", listOf(LeafText("inner", TextField.EMAIL, "new")))))
        assertEquals(expected, result)
    }

    // ─── removeChild ─────────────────────────────────────────────────────────

    @Test
    fun `removeChild from BranchAnd removes the matching child`() {
        val c1 = LeafText("c1", TextField.EMAIL, "x")
        val c2 = LeafPhone("c2", "+1*")
        val branch = BranchAnd("root", listOf(c1, c2))
        val result = branch.removeChild("c1")
        assertEquals(BranchAnd("root", listOf(c2)), result)
    }

    @Test
    fun `removeChild from BranchOr removes the matching child`() {
        val c1 = LeafText("c1", TextField.EMAIL, "x")
        val c2 = LeafPhone("c2", "+1*")
        val branch = BranchOr("root", listOf(c1, c2))
        val result = branch.removeChild("c2")
        assertEquals(BranchOr("root", listOf(c1)), result)
    }

    @Test
    fun `removeChild from BranchNot when matching sets child to null`() {
        val child = LeafPredicate("c", PredicateKind.NO_EMAIL)
        val branch = BranchNot("root", child)
        val result = branch.removeChild("c")
        assertEquals(BranchNot("root", null), result)
    }

    @Test
    fun `removeChild on non-matching is a no-op`() {
        val c1 = LeafText("c1", TextField.EMAIL, "x")
        val branch = BranchAnd("root", listOf(c1))
        val result = branch.removeChild("zzz")
        assertEquals(branch, result)
    }

    @Test
    fun `removeChild on leaf is a no-op`() {
        val leaf = LeafText("leaf", TextField.EMAIL, "x")
        val result = leaf.removeChild("leaf")
        assertEquals(leaf, result)
    }

    // ─── addChild ────────────────────────────────────────────────────────────

    @Test
    fun `addChild to BranchAnd with matching id appends child`() {
        val existing = LeafPhone("c1", "+1*")
        val branch = BranchAnd("root", listOf(existing))
        val newChild = LeafText("c2", TextField.EMAIL, "new")
        val result = branch.addChild("root", newChild)
        assertEquals(BranchAnd("root", listOf(existing, newChild)), result)
    }

    @Test
    fun `addChild to BranchOr with matching id appends child`() {
        val branch = BranchOr("root", emptyList())
        val newChild = LeafPredicate("c1", PredicateKind.NO_EMAIL)
        val result = branch.addChild("root", newChild)
        assertEquals(BranchOr("root", listOf(newChild)), result)
    }

    @Test
    fun `addChild to BranchNot with matching id sets child when empty`() {
        val branch = BranchNot("root", null)
        val newChild = LeafPredicate("c1", PredicateKind.NO_EMAIL)
        val result = branch.addChild("root", newChild)
        assertEquals(BranchNot("root", newChild), result)
    }

    @Test
    fun `addChild to non-matching node is a no-op`() {
        val branch = BranchAnd("root", emptyList())
        val newChild = LeafText("c2", TextField.EMAIL, "x")
        val result = branch.addChild("zzz", newChild)
        assertEquals(branch, result)
    }

    // ─── toBuilderNode round-trip ─────────────────────────────────────────────

    @Test
    fun `round-trip deep nested condition`() {
        // And(of=[Or(of=[TextMatch(EMAIL,"*@x.com"), PhoneMatch("+1*")]), Not(Predicate(NO_EMAIL))])
        val original =
            And(
                of =
                    listOf(
                        Or(of = listOf(TextMatch(TextField.EMAIL, "*@x.com"), PhoneMatch("+1*"))),
                        Not(of = Predicate(PredicateKind.NO_EMAIL)),
                    ),
            )
        idCounter = 0
        val builder = original.toBuilderNode { nextId() }
        val restored = builder.toConditionOrNull()
        assertEquals(original, restored)
    }

    @Test
    fun `toBuilderNode for TextMatch yields LeafText`() {
        idCounter = 0
        val node = TextMatch(TextField.NAME, "Bob").toBuilderNode { nextId() }
        val expected = LeafText("n0", TextField.NAME, "Bob")
        assertEquals(expected, node)
    }

    @Test
    fun `toBuilderNode for PhoneMatch yields LeafPhone`() {
        idCounter = 0
        val node = PhoneMatch("+44*").toBuilderNode { nextId() }
        assertEquals(LeafPhone("n0", "+44*"), node)
    }

    @Test
    fun `toBuilderNode for Predicate yields LeafPredicate`() {
        idCounter = 0
        val instant = Instant.parse("2024-06-01T00:00:00Z")
        val node = Predicate(PredicateKind.CREATED_BEFORE, before = instant).toBuilderNode { nextId() }
        assertEquals(LeafPredicate("n0", PredicateKind.CREATED_BEFORE, before = instant), node)
    }

    @Test
    fun `toBuilderNode for And yields BranchAnd with children`() {
        idCounter = 0
        val cond = And(of = listOf(TextMatch(TextField.EMAIL, "x"), PhoneMatch("+1*")))
        val node = cond.toBuilderNode { nextId() }
        assertNotNull(node as? BranchAnd)
        assertEquals(2, (node as BranchAnd).children.size)
    }
}

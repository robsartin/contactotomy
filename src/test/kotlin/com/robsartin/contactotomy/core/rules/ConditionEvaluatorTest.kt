package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionEvaluatorTest {
    private val eval = ConditionEvaluator()

    @Test fun `text match yields a reason`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        assertEquals("email matches *@indeed.com", eval.matchReason(TextMatch(TextField.EMAIL, "*@indeed.com"), c))
    }

    @Test fun `and requires all, reason joins matched leaves`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        val cond = And(listOf(TextMatch(TextField.EMAIL, "*@indeed.com"), Predicate(PredicateKind.NO_NAME_AND_NO_PHONE)))
        assertTrue(eval.matchReason(cond, c)!!.contains("AND"))
        val withName = contact("2", given = "Al", emails = listOf("bob@indeed.com"))
        assertNull(eval.matchReason(cond, withName)) // predicate fails -> AND fails
    }

    @Test fun `or names only the satisfied branch`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        val cond = Or(listOf(TextMatch(TextField.EMAIL, "*@indeed.com"), TextMatch(TextField.EMAIL, "*@oldjob.com")))
        assertEquals("email matches *@indeed.com", eval.matchReason(cond, c))
    }

    @Test fun `not negates`() {
        val c = contact("1", emails = listOf("a@b.com"))
        // NOT(NO_EMAIL) is true because the card HAS an email
        assertTrue(eval.matchReason(Not(Predicate(PredicateKind.NO_EMAIL)), c)!!.startsWith("NOT"))
        // NOT(has email) i.e. NOT(NOT NO_EMAIL)... simpler: NOT over a matching condition -> null
        assertNull(eval.matchReason(Not(TextMatch(TextField.EMAIL, "*@b.com")), c))
    }

    @Test fun `empty and is true, empty or is false`() {
        val c = contact("1")
        assertTrue(eval.matchReason(And(emptyList()), c) != null)
        assertNull(eval.matchReason(Or(emptyList()), c))
    }
}

package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleEngineTest {
    @Test fun `flags matching contacts with reasons, omits non-matching`() {
        val indeed = contact("1", emails = listOf("bob@indeed.com"))
        val keep = contact("2", emails = listOf("bob@personal.com"))
        val ruleSet = RuleSet(listOf(Rule("old job", TextMatch(TextField.EMAIL, "*@indeed.com"))))

        val flagged = RuleEngine.evaluate(listOf(indeed, keep), ruleSet)

        assertEquals(listOf("1"), flagged.map { it.contact.id })
        assertEquals(
            "old job",
            flagged
                .single()
                .matches
                .single()
                .ruleName,
        )
    }

    @Test fun `a contact hit by multiple rules appears once with multiple matches`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        val ruleSet =
            RuleSet(
                listOf(
                    Rule("indeed", TextMatch(TextField.EMAIL, "*@indeed.com")),
                    Rule("any email", TextMatch(TextField.EMAIL, "*")),
                ),
            )
        val flagged = RuleEngine.evaluate(listOf(c), ruleSet)
        assertEquals(1, flagged.size)
        assertEquals(2, flagged.single().matches.size)
    }

    @Test fun `applyDeletions removes approved ids and preserves order`() {
        val a = contact("a")
        val b = contact("b")
        val c = contact("c")
        assertEquals(listOf("a", "c"), applyDeletions(listOf(a, b, c), setOf("b")).map { it.id })
    }

    private fun contact(
        id: String,
        emails: List<String> = emptyList(),
    ) = Contact(
        id = id,
        source = Source.APPLE,
        name = ContactName(given = "X"),
        emails = emails,
        rawVCard = "",
    )
}

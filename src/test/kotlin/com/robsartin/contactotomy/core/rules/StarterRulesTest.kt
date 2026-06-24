package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterRulesTest {
    @Test fun `starter set contains the seed rules and round-trips`() {
        val starter = RuleSet.starter()
        assertEquals(4, starter.rules.size)
        assertEquals(starter, RuleStore.fromJson(RuleStore.toJson(starter)))
    }

    @Test fun `starter flags an indeed address`() {
        val c =
            Contact(
                id = "1",
                source = Source.APPLE,
                name = ContactName(given = "Al"),
                emails = listOf("al@indeed.com"),
                rawVCard = "",
            )
        val flagged = RuleEngine.evaluate(listOf(c), RuleSet.starter())
        assertTrue(flagged.single().matches.any { it.reason.contains("@indeed.com") })
    }
}

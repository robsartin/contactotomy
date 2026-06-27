package com.robsartin.contactotomy.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterRulesTest {
    private val starter = RuleSet.starter()

    @Test fun `starter set has the ten curated rules`() {
        assertEquals(10, starter.rules.size)
        val names = starter.rules.map { it.name }.toSet()
        assertTrue(
            names.containsAll(
                setOf(
                    "old job (indeed)",
                    "my own addresses",
                    "austin area code",
                    "no name and no phone",
                    "empty cards",
                    "name is an email address",
                    "no-reply senders",
                    "premium rate (1-900)",
                    "placeholder names",
                    "automated sender with no identity",
                ),
            ),
            "missing starters: $names",
        )
    }

    @Test fun `new safe-default conditions are correct`() {
        val by = starter.rules.associateBy { it.name }
        assertEquals(Predicate(PredicateKind.EMPTY_CARD), by["empty cards"]?.condition)
        assertEquals(TextMatch(TextField.NAME, "*@*"), by["name is an email address"]?.condition)
        assertEquals(PhoneMatch("900-???-????"), by["premium rate (1-900)"]?.condition)
    }
}

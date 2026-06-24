package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleTypesTest {
    @Test
    fun `rule holds a name and a condition tree`() {
        val rule =
            Rule(
                name = "junk",
                condition =
                    And(
                        listOf(
                            TextMatch(TextField.EMAIL, "*@indeed.com"),
                            Not(Predicate(PredicateKind.NO_EMAIL)),
                        ),
                    ),
            )
        assertEquals("junk", rule.name)
        assertEquals(2, (rule.condition as And).of.size)
    }

    @Test
    fun `flagged groups a contact with its rule matches`() {
        val c = Contact(id = "1", source = Source.APPLE, name = ContactName(given = "A"), rawVCard = "")
        val flagged = Flagged(c, listOf(RuleMatch("junk", "no email")))
        assertEquals("1", flagged.contact.id)
        assertEquals("junk", flagged.matches.single().ruleName)
    }
}

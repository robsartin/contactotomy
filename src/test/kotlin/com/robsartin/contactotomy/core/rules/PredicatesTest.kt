package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredicatesTest {
    private fun p(
        kind: PredicateKind,
        before: Instant? = null,
        source: Source? = null,
    ) = Predicate(kind, before, source)

    @Test fun `no name and no phone`() {
        assertTrue(Predicates.evaluate(p(PredicateKind.NO_NAME_AND_NO_PHONE), contact("1")))
        assertFalse(Predicates.evaluate(p(PredicateKind.NO_NAME_AND_NO_PHONE), contact("1", given = "Al")))
        assertFalse(Predicates.evaluate(p(PredicateKind.NO_NAME_AND_NO_PHONE), contact("1", phones = listOf("+1"))))
    }

    @Test fun `no email and empty card`() {
        assertTrue(Predicates.evaluate(p(PredicateKind.NO_EMAIL), contact("1", given = "Al")))
        assertFalse(Predicates.evaluate(p(PredicateKind.NO_EMAIL), contact("1", emails = listOf("a@b.com"))))
        assertTrue(Predicates.evaluate(p(PredicateKind.EMPTY_CARD), contact("1")))
        assertFalse(Predicates.evaluate(p(PredicateKind.EMPTY_CARD), contact("1", notes = "hi")))
    }

    @Test fun `created before respects null createdAt`() {
        val cutoff = Instant.parse("2020-01-01T00:00:00Z")
        val old = contact("1", given = "Al", createdAt = Instant.parse("2015-01-01T00:00:00Z"))
        val new = contact("2", given = "Al", createdAt = Instant.parse("2024-01-01T00:00:00Z"))
        val unknown = contact("3", given = "Al", createdAt = null)
        assertTrue(Predicates.evaluate(p(PredicateKind.CREATED_BEFORE, before = cutoff), old))
        assertFalse(Predicates.evaluate(p(PredicateKind.CREATED_BEFORE, before = cutoff), new))
        assertFalse(Predicates.evaluate(p(PredicateKind.CREATED_BEFORE, before = cutoff), unknown))
    }

    @Test fun `source is and never contacted stub`() {
        assertTrue(
            Predicates.evaluate(
                p(PredicateKind.SOURCE_IS, source = Source.GOOGLE),
                contact("1", source = Source.GOOGLE),
            ),
        )
        assertFalse(
            Predicates.evaluate(
                p(PredicateKind.SOURCE_IS, source = Source.APPLE),
                contact("1", source = Source.GOOGLE),
            ),
        )
        assertFalse(Predicates.evaluate(p(PredicateKind.NEVER_CONTACTED), contact("1")))
    }

    private fun contact(
        id: String,
        given: String? = null,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
        notes: String? = null,
        source: Source = Source.APPLE,
        createdAt: Instant? = null,
    ) = Contact(
        id = id,
        source = source,
        name = ContactName(given = given),
        phones = phones,
        rawPhones = phones,
        emails = emails,
        notes = notes,
        createdAt = createdAt,
        rawVCard = "",
    )
}

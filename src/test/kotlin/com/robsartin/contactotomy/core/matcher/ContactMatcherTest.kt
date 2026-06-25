package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactMatcherTest {
    private val matcher =
        ContactMatcher(
            EdgeClassifier(NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob"))))),
        )

    @Test
    fun `transitive chain over HIGH edges forms one cluster`() {
        // a-b share a phone; b-c share an email; a and c share nothing directly.
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1111"), emails = listOf("r@x.com"))
        val c = contact("c", given = "Bob", family = "Sartin", emails = listOf("r@x.com"))
        val result = matcher.match(listOf(a, b, c))
        assertEquals(1, result.clusters.size)
        assertEquals(
            setOf("a", "b", "c"),
            result.clusters
                .first()
                .members
                .map { it.id }
                .toSet(),
        )
    }

    @Test
    fun `uncertain name-only pair is not clustered but surfaced`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val result = matcher.match(listOf(a, b))
        assertTrue(result.clusters.isEmpty())
        assertEquals(1, result.uncertainPairs.size)
    }

    @Test
    fun `married couple sharing a phone yields no cluster and no uncertain pair`() {
        val alice = contact("a", given = "Alice", family = "Smith", phones = listOf("+1999"))
        val bob = contact("b", given = "Bob", family = "Smith", phones = listOf("+1999"))
        val result = matcher.match(listOf(alice, bob))
        assertTrue(result.clusters.isEmpty())
        assertTrue(result.uncertainPairs.isEmpty())
    }

    @Test
    fun `reasons and uncertain pairs are deterministically ordered`() {
        // HIGH cluster: two Robert/Rob contacts share both a phone and an email
        // (nickname given match) => reasons SHARED_PHONE, SHARED_EMAIL, NAME_NICKNAME.
        val p1 = contact("p1", given = "Robert", family = "Stone", phones = listOf("+15"), emails = listOf("rs@x.com"))
        val p2 = contact("p2", given = "Rob", family = "Stone", phones = listOf("+15"), emails = listOf("rs@x.com"))
        // Two name-only uncertain pairs, ids intentionally out of natural order.
        val z = contact("z", given = "Jane", family = "Doe")
        val m = contact("m", given = "Jane", family = "Doe")
        val a = contact("a", given = "Karl", family = "Frey")
        val k = contact("k", given = "Karl", family = "Frey")

        val result = matcher.match(listOf(p1, p2, z, m, a, k))

        // uncertainPairs sorted by (a.id, b.id): a<k pair first, then m<z pair.
        assertEquals(
            listOf("a" to "k", "m" to "z"),
            result.uncertainPairs.map { it.a.id to it.b.id },
        )

        val cluster = result.clusters.single()
        assertEquals(
            listOf(MatchReason.SHARED_PHONE, MatchReason.SHARED_EMAIL, MatchReason.NAME_NICKNAME),
            cluster.reasons,
        )
    }

    @Test
    fun `cluster id is deterministic from member ids`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1111"))
        assertEquals(
            matcher
                .match(listOf(a, b))
                .clusters
                .first()
                .id,
            matcher
                .match(listOf(b, a))
                .clusters
                .first()
                .id,
        )
    }
}

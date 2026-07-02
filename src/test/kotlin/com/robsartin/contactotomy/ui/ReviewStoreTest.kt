package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for ReviewStore: verifies Section 1 (merges via MergeReviewStore) and
 * Section 2 (singleton tidy candidates) are built correctly and that commit()
 * chains both transforms.
 */
class ReviewStoreTest {
    // Fixture: a duplicate pair (same phone → HIGH cluster)
    private val rob = contact("rob", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
    private val robert = contact("robert", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))

    // Fixture: a lone company card (no org) — should be in Section 2, COMPANY action
    private val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc"))

    // Fixture: a lone nameless card with an email — should be in Section 2, EMAIL_NAME action
    private val emailOnly = contact("email", emails = listOf("lonely@example.com"))

    // Fixture: a plain person with a name and phone — not suggested (not in Section 2)
    private val jane = contact("jane", given = "Jane", family = "Smith", phones = listOf("+15125559999"))

    private val allContacts = listOf(rob, robert, acme, emailOnly, jane)

    @Test
    fun `cleanCandidates returns only suggested singletons - not paired and not plain person`() {
        val store = ReviewStore(allContacts)
        val candidates = store.cleanCandidates()
        val ids = candidates.map { it.id }.toSet()
        assertTrue("acme" in ids, "Acme Inc (company name) should be a clean candidate")
        assertTrue("email" in ids, "nameless email card should be a clean candidate")
        assertFalse("rob" in ids, "paired card should not be a singleton candidate")
        assertFalse("robert" in ids, "paired card should not be a singleton candidate")
        assertFalse("jane" in ids, "plain person should not be a clean candidate")
    }

    @Test
    fun `both suggested candidates are pre-marked by default`() {
        val store = ReviewStore(allContacts)
        val marked = store.markedIds
        assertTrue("acme" in marked, "acme should be pre-marked")
        assertTrue("email" in marked, "email should be pre-marked")
        assertFalse("rob" in marked, "rob should not be pre-marked")
        assertFalse("jane" in marked, "jane should not be pre-marked")
    }

    @Test
    fun `toggleClean flips a mark`() {
        val store = ReviewStore(allContacts)
        assertTrue("acme" in store.markedIds)
        store.toggleClean("acme")
        assertFalse("acme" in store.markedIds)
        store.toggleClean("acme")
        assertTrue("acme" in store.markedIds)
    }

    @Test
    fun `toggleClean can mark a non-default candidate`() {
        val store = ReviewStore(allContacts)
        assertFalse("jane" in store.markedIds)
        store.toggleClean("jane")
        assertTrue("jane" in store.markedIds)
    }

    @Test
    fun `actionFor returns COMPANY for company-name card`() {
        val store = ReviewStore(allContacts)
        assertEquals(TidyAction.COMPANY, store.actionFor(acme))
    }

    @Test
    fun `actionFor returns EMAIL_NAME for nameless email card`() {
        val store = ReviewStore(allContacts)
        assertEquals(TidyAction.EMAIL_NAME, store.actionFor(emailOnly))
    }

    @Test
    fun `commit with merge accepted and both clean cards marked applies both transforms`() {
        val store = ReviewStore(allContacts)
        // Accept the merge in Section 1
        val mergeItems = store.mergeStore.state.value.items
        assertEquals(1, mergeItems.size, "expected exactly one merge cluster for rob+robert")
        store.mergeStore.accept(mergeItems.first().id)

        // Both acme and email are pre-marked — no toggles needed
        val result = store.commit()
        val byId = result.associateBy { it.id }

        // Pair collapsed into one contact
        val mergedIds = byId.keys.filter { it != "acme" && it != "email" && it != "jane" }
        assertEquals(1, mergedIds.size, "duplicate pair should collapse to 1 contact; got $mergedIds")

        // Acme normalized to org
        val acmeOut = byId["acme"]
        assertFalse(acmeOut == null, "acme should survive in output")
        assertEquals("Acme Inc", acmeOut!!.org)
        assertEquals(ContactName(), acmeOut.name)

        // Email card named from email
        val emailOut = byId["email"]
        assertFalse(emailOut == null, "email card should survive in output")
        assertEquals(ContactName(formatted = "lonely@example.com"), emailOut!!.name)

        // Jane unchanged
        val janeOut = byId["jane"]
        assertFalse(janeOut == null, "jane should survive in output")
        assertEquals("Jane", janeOut!!.name.given)

        // Total: 1 merged + acme + email + jane = 4
        assertEquals(4, result.size)
    }

    @Test
    fun `commit with clean card unticked leaves that card unchanged`() {
        val store = ReviewStore(allContacts)
        // Untick acme so it should not be transformed
        store.toggleClean("acme")
        assertFalse("acme" in store.markedIds)

        val result = store.commit()
        val byId = result.associateBy { it.id }

        // acme should be unchanged (still has name, no org)
        val acmeOut = byId["acme"]
        assertFalse(acmeOut == null)
        assertEquals(null, acmeOut!!.org)
        assertEquals(ContactName(formatted = "Acme Inc"), acmeOut.name)

        // email is still pre-marked and transformed
        val emailOut = byId["email"]
        assertFalse(emailOut == null)
        assertEquals(ContactName(formatted = "lonely@example.com"), emailOut!!.name)
    }

    @Test
    fun `with no duplicates cleanCandidates and commit still work`() {
        val store = ReviewStore(listOf(acme, emailOnly, jane))
        val candidates = store.cleanCandidates().map { it.id }.toSet()
        assertTrue("acme" in candidates)
        assertTrue("email" in candidates)
        assertFalse("jane" in candidates)

        val result = store.commit()
        assertEquals(3, result.size)
        val byId = result.associateBy { it.id }
        assertEquals("Acme Inc", byId["acme"]!!.org)
        assertEquals(ContactName(formatted = "lonely@example.com"), byId["email"]!!.name)
        assertEquals("Jane", byId["jane"]!!.name.given)
    }

    @Test
    fun `with no clean candidates commit applies only merge transforms`() {
        // All contacts are in the duplicate pair, no singletons
        val store = ReviewStore(listOf(rob, robert))
        assertEquals(0, store.cleanCandidates().size)
        val mergeItem =
            store.mergeStore.state.value.items
                .first()
        store.mergeStore.accept(mergeItem.id)
        val result = store.commit()
        assertEquals(1, result.size)
    }
}

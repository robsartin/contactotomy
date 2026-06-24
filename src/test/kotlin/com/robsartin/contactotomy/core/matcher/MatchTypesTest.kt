package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchTypesTest {
    private fun c(id: String) = Contact(id = id, source = Source.APPLE, name = ContactName(given = id), rawVCard = "")

    @Test
    fun `match edge carries confidence and reasons`() {
        val edge = MatchEdge(c("a"), c("b"), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE, MatchReason.NAME_EXACT))
        assertEquals(Confidence.HIGH, edge.confidence)
        assertEquals(listOf(MatchReason.SHARED_PHONE, MatchReason.NAME_EXACT), edge.reasons)
    }

    @Test
    fun `match result holds clusters and uncertain pairs`() {
        val cluster = Cluster("cluster-a+b", listOf(c("a"), c("b")), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))
        val result = MatchResult(clusters = listOf(cluster), uncertainPairs = emptyList())
        assertEquals(1, result.clusters.size)
        assertEquals("cluster-a+b", result.clusters.first().id)
    }
}

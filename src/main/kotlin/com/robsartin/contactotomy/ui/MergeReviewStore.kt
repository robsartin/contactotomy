package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.ContactMatcher
import com.robsartin.contactotomy.core.matcher.EdgeClassifier
import com.robsartin.contactotomy.core.matcher.NameMatcher
import com.robsartin.contactotomy.core.matcher.NicknameDictionary
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds merge-review state built from the imported contacts via the core engine. */
class MergeReviewStore(
    private val contacts: List<Contact>,
    private val matcher: ContactMatcher = defaultMatcher(),
    private val merger: ContactMerger = ContactMerger(),
) {
    private val _state = MutableStateFlow(MergeReviewState(items = buildItems()))
    val state: StateFlow<MergeReviewState> = _state.asStateFlow()

    private fun buildItems(): List<ReviewItem> {
        val result = matcher.match(contacts)
        val high =
            result.clusters.map { cluster ->
                ReviewItem(id = cluster.id, origin = Origin.HIGH, proposal = merger.merge(cluster), decision = Decision.ACCEPT)
            }
        val uncertain =
            result.uncertainPairs.map { edge ->
                val cluster =
                    Cluster(
                        id = "uncertain-${edge.a.id}+${edge.b.id}",
                        members = listOf(edge.a, edge.b),
                        confidence = Confidence.UNCERTAIN,
                        reasons = edge.reasons,
                    )
                ReviewItem(id = cluster.id, origin = Origin.UNCERTAIN, proposal = merger.merge(cluster), decision = Decision.REJECT)
            }
        return high + uncertain
    }

    companion object {
        fun defaultMatcher(): ContactMatcher = ContactMatcher(EdgeClassifier(NameMatcher(NicknameDictionary.fromResource())))
    }
}

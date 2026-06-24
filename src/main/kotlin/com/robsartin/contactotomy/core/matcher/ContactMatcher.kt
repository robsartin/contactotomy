package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

/** Turns a flat contact list into HIGH-confidence clusters plus UNCERTAIN review pairs. */
class ContactMatcher(
    private val classifier: EdgeClassifier,
    private val indexFactory: (List<Contact>) -> CandidateIndex = ::CandidateIndex,
) {
    fun match(contacts: List<Contact>): MatchResult {
        val edges =
            indexFactory(contacts)
                .candidatePairs()
                .mapNotNull { (a, b) -> classifier.classify(a, b) }

        val highEdges = edges.filter { it.confidence == Confidence.HIGH }
        val uncertainEdges = edges.filter { it.confidence == Confidence.UNCERTAIN }

        val uf = UnionFind(contacts.map { it.id })
        highEdges.forEach { uf.union(it.a.id, it.b.id) }

        val byId = contacts.associateBy { it.id }
        val reasonsByRoot = highEdges.groupBy { uf.find(it.a.id) }

        val clusters =
            uf
                .groups()
                .filterValues { it.size >= 2 }
                .map { (root, ids) ->
                    val members = ids.map { byId.getValue(it) }.sortedBy { it.id }
                    val reasons =
                        reasonsByRoot[root]
                            .orEmpty()
                            .flatMap { it.reasons }
                            .distinct()
                            .sortedBy { it.ordinal }
                    Cluster(clusterId(members), members, Confidence.HIGH, reasons)
                }.sortedBy { it.id }

        val clusterIdByMember =
            clusters
                .flatMap { c -> c.members.map { it.id to c.id } }
                .toMap()

        // Drop uncertain pairs already unified inside one HIGH cluster.
        val uncertainPairs =
            uncertainEdges
                .filter {
                    val ca = clusterIdByMember[it.a.id]
                    val cb = clusterIdByMember[it.b.id]
                    ca == null || cb == null || ca != cb
                }.sortedWith(compareBy({ it.a.id }, { it.b.id }))

        return MatchResult(clusters, uncertainPairs)
    }

    private fun clusterId(members: List<Contact>): String = "cluster-" + members.map { it.id }.sorted().joinToString("+")
}

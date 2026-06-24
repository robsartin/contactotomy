package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import java.time.Instant

/** Builds a MergeProposal for a cluster: newest-wins single values, unioned multi-values. */
class ContactMerger {

    fun merge(cluster: Cluster): MergeProposal {
        // Members ordered newest-first; ties broken by id for determinism.
        val ordered = cluster.members.sortedWith(
            compareByDescending<Contact> { it.modifiedAt ?: Instant.MIN }.thenBy { it.id }
        )
        val primary = ordered.first()

        val merged = Contact(
            id = mergedId(cluster),
            source = primary.source,
            name = primary.name,
            phones = union(ordered.map { it.phones }),
            rawPhones = union(ordered.map { it.rawPhones }),
            emails = union(ordered.map { it.emails }),
            addresses = union(ordered.map { it.addresses }),
            org = ordered.firstNotNullOfOrNull { it.org },
            title = ordered.firstNotNullOfOrNull { it.title },
            urls = union(ordered.map { it.urls }),
            notes = ordered.firstNotNullOfOrNull { it.notes },
            categories = union(ordered.map { it.categories }),
            createdAt = cluster.members.mapNotNull { it.createdAt }.minOrNull(),
            modifiedAt = primary.modifiedAt,
            rawVCard = primary.rawVCard,
        )

        return MergeProposal(cluster, merged, provenance = emptyList(), conflicts = emptyList())
    }

    private fun union(lists: List<List<String>>): List<String> {
        val out = LinkedHashSet<String>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    private fun mergedId(cluster: Cluster): String =
        "merged-" + cluster.members.map { it.id }.sorted().joinToString("+")
}

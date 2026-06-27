package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.toDisplayString
import java.time.Instant

/** Builds a MergeProposal for a cluster, with provenance, conflicts, and most-complete name. */
class ContactMerger {
    fun merge(cluster: Cluster): MergeProposal {
        val ordered =
            cluster.members.sortedWith(
                compareByDescending<Contact> { it.modifiedAt ?: Instant.MIN }.thenBy { it.id },
            )
        val primary = ordered.first()
        val name = mostCompleteName(ordered)

        val phones = union(ordered.map { it.phones })
        val rawPhones = union(ordered.map { it.rawPhones })
        val emails = union(ordered.map { it.emails })
        val addresses = unionOf(ordered.map { it.addresses })
        val urls = union(ordered.map { it.urls })
        val categories = union(ordered.map { it.categories })

        val org = ordered.firstNotNullOfOrNull { it.org }
        val title = ordered.firstNotNullOfOrNull { it.title }
        val notes = ordered.firstNotNullOfOrNull { it.notes }

        val merged =
            Contact(
                id = mergedId(cluster),
                source = primary.source,
                name = name,
                phones = phones,
                rawPhones = rawPhones,
                emails = emails,
                addresses = addresses,
                org = org,
                title = title,
                urls = urls,
                notes = notes,
                categories = categories,
                createdAt = cluster.members.mapNotNull { it.createdAt }.minOrNull(),
                modifiedAt = primary.modifiedAt,
                rawVCard = primary.rawVCard,
            )

        val provenance =
            buildList {
                addAll(multiProvenance("phones", phones, ordered) { it.phones })
                addAll(multiProvenance("emails", emails, ordered) { it.emails })
                addAll(addressProvenance(addresses, ordered))
                addAll(multiProvenance("urls", urls, ordered) { it.urls })
                addAll(multiProvenance("categories", categories, ordered) { it.categories })
                org?.let { add(singleProvenance("org", it, ordered) { c -> c.org }) }
                title?.let { add(singleProvenance("title", it, ordered) { c -> c.title }) }
                notes?.let { add(singleProvenance("notes", it, ordered) { c -> c.notes }) }
            }

        val conflicts =
            listOfNotNull(
                conflictFor("org", ordered) { it.org },
                conflictFor("title", ordered) { it.title },
                conflictFor("notes", ordered) { it.notes },
            )

        return MergeProposal(cluster, merged, provenance, conflicts)
    }

    private fun mostCompleteName(ordered: List<Contact>): ContactName =
        ordered.maxByOrNull { completeness(it.name) }?.name ?: ordered.first().name

    private fun completeness(n: ContactName): Int = listOf(n.prefix, n.given, n.middle, n.family, n.suffix).count { !it.isNullOrBlank() }

    private fun multiProvenance(
        field: String,
        values: List<String>,
        members: List<Contact>,
        getter: (Contact) -> List<String>,
    ): List<FieldProvenance> =
        values.map { value ->
            FieldProvenance(field, value, members.filter { value in getter(it) }.map { it.id })
        }

    private fun singleProvenance(
        field: String,
        value: String,
        members: List<Contact>,
        getter: (Contact) -> String?,
    ): FieldProvenance = FieldProvenance(field, value, members.filter { getter(it) == value }.map { it.id })

    private fun conflictFor(
        field: String,
        ordered: List<Contact>,
        getter: (Contact) -> String?,
    ): FieldConflict? {
        val candidates = ordered.mapNotNull { c -> getter(c)?.let { ConflictCandidate(it, c.id, c.modifiedAt) } }
        if (candidates.map { it.value }.distinct().size < 2) return null
        return FieldConflict(field, candidates, chosen = candidates.first().value)
    }

    private fun union(lists: List<List<String>>): List<String> {
        val out = LinkedHashSet<String>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    private fun unionOf(lists: List<List<PostalAddress>>): List<PostalAddress> {
        val out = LinkedHashSet<PostalAddress>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    private fun addressProvenance(
        addresses: List<PostalAddress>,
        members: List<Contact>,
    ): List<FieldProvenance> =
        addresses.map { addr ->
            FieldProvenance(
                "addresses",
                addr.toDisplayString(),
                members.filter { addr in it.addresses }.map { it.id },
            )
        }

    private fun mergedId(cluster: Cluster): String =
        "merged-" +
            cluster.members
                .map { it.id }
                .sorted()
                .joinToString("+")
}

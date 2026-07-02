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
        val emails = unionCaseInsensitive(ordered.map { it.emails })
        val addresses = unionByDisplayString(ordered.map { it.addresses })
        val urls = union(ordered.map { it.urls })
        val categories = union(ordered.map { it.categories })

        val org = ordered.firstNotNullOfOrNull { it.org }
        val title = ordered.firstNotNullOfOrNull { it.title }
        val notes = ordered.firstNotNullOfOrNull { it.notes }
        val photo = ordered.firstNotNullOfOrNull { it.photo }

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
                photo = photo,
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

    /** Unions string lists, deduplicating case-insensitively and preserving the first occurrence's casing. */
    private fun unionCaseInsensitive(lists: List<List<String>>): List<String> {
        val seen = LinkedHashSet<String>() // lowercase sentinels
        val out = mutableListOf<String>()
        lists.forEach { list ->
            list.forEach { value ->
                val lower = value.lowercase()
                if (seen.add(lower)) {
                    out.add(value)
                }
            }
        }
        return out
    }

    private fun unionOf(lists: List<List<PostalAddress>>): List<PostalAddress> {
        val out = LinkedHashSet<PostalAddress>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    /** Unions address lists, deduplicating by display string and preserving first occurrence. */
    private fun unionByDisplayString(lists: List<List<PostalAddress>>): List<PostalAddress> {
        val seen = LinkedHashSet<String>() // display string sentinels
        val out = mutableListOf<PostalAddress>()
        lists.forEach { list ->
            list.forEach { addr ->
                val key = addr.toDisplayString()
                if (seen.add(key)) {
                    out.add(addr)
                }
            }
        }
        return out
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

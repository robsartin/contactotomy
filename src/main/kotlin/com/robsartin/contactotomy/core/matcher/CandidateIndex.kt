package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

/** Generates candidate pairs by blocking on shared phone, email, or family name. */
class CandidateIndex(
    private val contacts: List<Contact>,
) {
    fun candidatePairs(): Set<Pair<Contact, Contact>> {
        val buckets = HashMap<String, MutableList<Contact>>()

        fun add(
            key: String,
            c: Contact,
        ) {
            if (key.isBlank()) return
            buckets.getOrPut(key) { mutableListOf() }.add(c)
        }
        for (c in contacts) {
            c.phones.forEach { add("phone:$it", c) }
            c.emails.forEach { add("email:$it", c) }
            familyKey(c)?.let { add("family:$it", c) }
        }

        val pairs = LinkedHashSet<Pair<Contact, Contact>>()
        for (bucket in buckets.values) {
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    if (a.id != b.id) pairs += orderPair(a, b)
                }
            }
        }
        return pairs
    }

    private fun familyKey(c: Contact): String? =
        c.name.family
            ?.lowercase()
            ?.trim()
            ?.ifEmpty { null }

    private fun orderPair(
        a: Contact,
        b: Contact,
    ): Pair<Contact, Contact> = if (a.id <= b.id) a to b else b to a
}

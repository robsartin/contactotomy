package com.robsartin.contactotomy.core.model

import java.time.Instant

enum class Source { APPLE, GOOGLE, FILE }

data class ContactName(
    val prefix: String? = null,
    val given: String? = null,
    val middle: String? = null,
    val family: String? = null,
    val suffix: String? = null,
    val formatted: String? = null,
)

data class Contact(
    val id: String,
    val source: Source,
    val name: ContactName,
    val phones: List<String> = emptyList(), // E.164-normalized
    val rawPhones: List<String> = emptyList(), // original strings
    val emails: List<String> = emptyList(), // lowercased, trimmed
    val addresses: List<PostalAddress> = emptyList(),
    val org: String? = null,
    val title: String? = null,
    val urls: List<String> = emptyList(),
    val notes: String? = null,
    val categories: List<String> = emptyList(), // Google labels (vCard CATEGORIES)
    val createdAt: Instant? = null, // best-effort
    val modifiedAt: Instant? = null, // best-effort (vCard REV)
    val rawVCard: String,
)

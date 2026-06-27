package com.robsartin.contactotomy.testsupport

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant

/**
 * Single shared test factory for Contact — imported by tests; never redeclared per class.
 *
 * Only the parameters 4b-1's tests actually use are exposed. This is deliberate
 * (YAGNI): do NOT add speculative parameters for fields a future plan *might*
 * need. When a later test needs another field (e.g. categories, createdAt, a
 * non-Apple source), add that one parameter then, in that plan. Param order lets
 * the common positional call `contact(id, given, family, phones)` work.
 */
fun contact(
    id: String,
    given: String? = null,
    family: String? = null,
    phones: List<String> = emptyList(),
    emails: List<String> = emptyList(),
    middle: String? = null,
    org: String? = null,
    notes: String? = null,
    categories: List<String> = emptyList(),
    modifiedAt: Instant? = null,
    createdAt: Instant? = null,
    source: Source = Source.APPLE,
    addresses: List<PostalAddress> = emptyList(),
    urls: List<String> = emptyList(),
) = Contact(
    id = id,
    source = source,
    name = ContactName(given = given, middle = middle, family = family),
    phones = phones,
    rawPhones = phones,
    emails = emails,
    addresses = addresses,
    org = org,
    notes = notes,
    urls = urls,
    categories = categories,
    modifiedAt = modifiedAt,
    createdAt = createdAt,
    rawVCard = "",
)

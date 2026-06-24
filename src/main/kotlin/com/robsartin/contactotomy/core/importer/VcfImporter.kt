package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.normalize.EmailNormalizer
import com.robsartin.contactotomy.core.normalize.PhoneNormalizer
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import java.time.Instant

class VcfImporter(
    private val source: Source,
    private val phoneNormalizer: PhoneNormalizer = PhoneNormalizer(),
) {
    /**
     * Parses vCard text into normalized Contacts, tagging each with [source].
     *
     * Note: the assigned [Contact.id] is positional (`"$source-$index"`) within a single import.
     * These ids are NOT stable across re-imports or reordering of the input, so downstream
     * consumers must not treat them as durable identity.
     */
    fun import(vcfText: String): List<Contact> =
        Ezvcard.parse(vcfText).all().mapIndexed { index, card -> toContact(card, index) }

    private fun toContact(card: VCard, index: Int): Contact {
        val rawPhones = card.telephoneNumbers.mapNotNull { it.text }
        val rawEmails = card.emails.mapNotNull { it.value }
        return Contact(
            id = "${source.name.lowercase()}-$index",
            source = source,
            name = toName(card),
            phones = rawPhones.mapNotNull { phoneNormalizer.normalize(it) },
            rawPhones = rawPhones,
            emails = rawEmails.mapNotNull { EmailNormalizer.normalize(it) },
            addresses = card.addresses.mapNotNull { it.streetAddress },
            org = card.organization?.values?.joinToString(" / ")?.ifEmpty { null },
            title = card.titles.firstOrNull()?.value,
            urls = card.urls.mapNotNull { it.value },
            notes = card.notes.firstOrNull()?.value,
            categories = card.categories?.values?.toList() ?: emptyList(),
            modifiedAt = card.revision?.value?.let { Instant.from(it) },
            rawVCard = Ezvcard.write(card).version(card.version ?: VCardVersion.V3_0).go(),
        )
    }

    private fun toName(card: VCard): ContactName {
        val n = card.structuredName
        return ContactName(
            prefix = n?.prefixes?.firstOrNull(),
            given = n?.given,
            middle = n?.additionalNames?.firstOrNull(),
            family = n?.family,
            suffix = n?.suffixes?.firstOrNull(),
            formatted = card.formattedName?.value,
        )
    }
}

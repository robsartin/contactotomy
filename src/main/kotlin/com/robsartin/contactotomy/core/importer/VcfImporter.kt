package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.normalize.EmailNormalizer
import com.robsartin.contactotomy.core.normalize.PhoneNormalizer
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import java.time.Instant
import java.util.Base64

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
    fun import(vcfText: String): List<Contact> = Ezvcard.parse(vcfText).all().mapIndexed { index, card -> toContact(card, index) }

    private fun toContact(
        card: VCard,
        index: Int,
    ): Contact {
        val rawPhones = card.telephoneNumbers.mapNotNull { it.text }
        val rawEmails = card.emails.mapNotNull { it.value }
        return Contact(
            id = "${source.name.lowercase()}-$index",
            source = source,
            name = toName(card),
            phones = rawPhones.mapNotNull { phoneNormalizer.normalize(it) },
            rawPhones = rawPhones,
            emails = rawEmails.mapNotNull { EmailNormalizer.normalize(it) },
            addresses = card.addresses.map { toPostalAddress(it) },
            org =
                card.organization
                    ?.values
                    ?.joinToString(" / ")
                    ?.ifEmpty { null },
            title = card.titles.firstOrNull()?.value,
            urls = card.urls.mapNotNull { it.value },
            notes = card.notes.firstOrNull()?.value,
            categories = card.categories?.values?.toList() ?: emptyList(),
            modifiedAt = card.revision?.value?.let { Instant.from(it) },
            photo = toContactPhoto(card),
            rawVCard = Ezvcard.write(card).version(card.version ?: VCardVersion.V3_0).go(),
        )
    }

    private fun imageTypeToMimeType(imageType: ezvcard.parameter.ImageType?): String? =
        when {
            imageType == null -> null
            imageType == ezvcard.parameter.ImageType.JPEG -> "image/jpeg"
            imageType == ezvcard.parameter.ImageType.PNG -> "image/png"
            imageType == ezvcard.parameter.ImageType.GIF -> "image/gif"
            else -> imageType.value?.let { "image/${it.lowercase()}" }
        }

    private fun toContactPhoto(card: VCard): ContactPhoto? {
        val photo = card.photos.firstOrNull() ?: return null
        val contentType = imageTypeToMimeType(photo.contentType)
        val data = photo.data
        return if (data != null && data.isNotEmpty()) {
            ContactPhoto(
                base64 = Base64.getEncoder().encodeToString(data),
                contentType = contentType,
            )
        } else {
            val url = photo.url ?: return null
            ContactPhoto(url = url, contentType = contentType)
        }
    }

    private fun toPostalAddress(addr: ezvcard.property.Address): PostalAddress =
        PostalAddress(
            poBox = addr.poBox?.takeIf { it.isNotEmpty() },
            extended = addr.extendedAddress?.takeIf { it.isNotEmpty() },
            street = addr.streetAddress?.takeIf { it.isNotEmpty() },
            city = addr.locality?.takeIf { it.isNotEmpty() },
            region = addr.region?.takeIf { it.isNotEmpty() },
            postalCode = addr.postalCode?.takeIf { it.isNotEmpty() },
            country = addr.country?.takeIf { it.isNotEmpty() },
        )

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

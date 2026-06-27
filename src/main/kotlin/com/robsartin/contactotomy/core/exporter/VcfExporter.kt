package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.PostalAddress
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.property.Categories
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import java.util.Base64

class VcfExporter {
    /** Serializes contacts to a single vCard 3.0, UTF-8 string. */
    fun export(contacts: List<Contact>): String = Ezvcard.write(contacts.map { toVCard(it) }).version(VCardVersion.V3_0).go()

    private fun toVCard(contact: Contact): VCard {
        val card = VCard()

        val structured =
            StructuredName().apply {
                family = contact.name.family
                given = contact.name.given
                contact.name.middle?.let { additionalNames.add(it) }
                contact.name.prefix?.let { prefixes.add(it) }
                contact.name.suffix?.let { suffixes.add(it) }
            }
        card.structuredName = structured
        val fn =
            contact.name.formatted
                ?: listOfNotNull(contact.name.given, contact.name.family).joinToString(" ")
        if (fn.isNotBlank()) card.setFormattedName(fn)

        contact.phones.forEach { card.addTelephoneNumber(it) }
        contact.emails.forEach { card.addEmail(it) }
        contact.addresses.forEach { card.addAddress(toEzAddress(it)) }
        contact.org?.let { card.setOrganization(it) }
        contact.title?.let { card.addTitle(it) }
        contact.urls.forEach { card.addUrl(it) }
        contact.notes?.let { card.addNote(it) }
        if (contact.categories.isNotEmpty()) {
            card.setCategories(Categories().apply { values.addAll(contact.categories) })
        }
        contact.photo?.let { toEzPhoto(it)?.let { ezPhoto -> card.addPhoto(ezPhoto) } }
        return card
    }

    private fun mimeTypeToImageType(contentType: String?): ImageType =
        when (contentType?.lowercase()) {
            "image/jpeg", "image/jpg" -> ImageType.JPEG
            "image/png" -> ImageType.PNG
            "image/gif" -> ImageType.GIF
            else -> ImageType.JPEG // sensible default
        }

    private fun toEzPhoto(photo: ContactPhoto): Photo? {
        val imageType = mimeTypeToImageType(photo.contentType)
        return when {
            photo.base64 != null -> Photo(Base64.getDecoder().decode(photo.base64), imageType)
            photo.url != null -> Photo(photo.url, imageType)
            else -> null
        }
    }

    private fun toEzAddress(addr: PostalAddress): ezvcard.property.Address =
        ezvcard.property.Address().apply {
            addr.poBox?.let { poBox = it }
            addr.extended?.let { extendedAddress = it }
            addr.street?.let { streetAddress = it }
            addr.city?.let { locality = it }
            addr.region?.let { region = it }
            addr.postalCode?.let { postalCode = it }
            addr.country?.let { country = it }
        }
}

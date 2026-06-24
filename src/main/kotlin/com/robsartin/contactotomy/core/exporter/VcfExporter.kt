package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.model.Contact
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Categories
import ezvcard.property.StructuredName

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
        contact.org?.let { card.setOrganization(it) }
        contact.title?.let { card.addTitle(it) }
        contact.urls.forEach { card.addUrl(it) }
        contact.notes?.let { card.addNote(it) }
        if (contact.categories.isNotEmpty()) {
            card.setCategories(Categories().apply { values.addAll(contact.categories) })
        }
        return card
    }
}

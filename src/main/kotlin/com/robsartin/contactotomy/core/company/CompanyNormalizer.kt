package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName

/** Turns a contact into a company-only card: name -> org (when org is blank), name cleared. */
object CompanyNormalizer {
    fun markAsCompany(contact: Contact): Contact =
        if (contact.org.isNullOrBlank()) {
            contact.copy(org = companyNameText(contact.name), name = ContactName())
        } else {
            contact.copy(name = ContactName())
        }

    /** Names a card from its first email (caller ensures emails is non-empty); emails are untouched. */
    fun nameFromEmail(contact: Contact): Contact = contact.copy(name = ContactName(formatted = contact.emails.first()))
}

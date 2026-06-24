package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact

internal object Predicates {
    fun evaluate(
        predicate: Predicate,
        contact: Contact,
    ): Boolean =
        when (predicate.kind) {
            PredicateKind.NO_NAME_AND_NO_PHONE -> !hasName(contact) && contact.phones.isEmpty()
            PredicateKind.NO_EMAIL -> contact.emails.isEmpty()
            PredicateKind.EMPTY_CARD ->
                !hasName(contact) &&
                    contact.phones.isEmpty() &&
                    contact.emails.isEmpty() &&
                    contact.org.isNullOrBlank() &&
                    contact.addresses.isEmpty() &&
                    contact.urls.isEmpty() &&
                    contact.notes.isNullOrBlank()
            PredicateKind.CREATED_BEFORE -> {
                val before = predicate.before
                val created = contact.createdAt
                before != null && created != null && created.isBefore(before)
            }
            PredicateKind.SOURCE_IS -> predicate.source != null && contact.source == predicate.source
            PredicateKind.NEVER_CONTACTED -> false
        }

    private fun hasName(c: Contact): Boolean =
        !c.name.given.isNullOrBlank() || !c.name.family.isNullOrBlank() || !c.name.formatted.isNullOrBlank()
}

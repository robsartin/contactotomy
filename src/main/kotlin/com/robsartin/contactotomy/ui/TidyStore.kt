package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.company.CompanyNameDetector
import com.robsartin.contactotomy.core.company.CompanyNormalizer
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class TidyAction { COMPANY, EMAIL_NAME }

data class TidyState(
    val markedIds: Set<String> = emptySet(),
)

class TidyStore(
    private val contacts: List<Contact>,
) {
    private val _state =
        MutableStateFlow(
            TidyState(
                markedIds = contacts.filter { suggested(it) }.map { it.id }.toSet(),
            ),
        )
    val state: StateFlow<TidyState> = _state.asStateFlow()

    /** The transform a card would get if marked. */
    fun actionFor(contact: Contact): TidyAction =
        if (companyNameText(contact.name).isBlank() && contact.org.isNullOrBlank() && contact.emails.isNotEmpty()) {
            TidyAction.EMAIL_NAME
        } else {
            TidyAction.COMPANY
        }

    /** Cards worth tidying: have a name (company candidate) or an email (email→name candidate). */
    fun listed(): List<Contact> = contacts.filter { companyNameText(it.name).isNotBlank() || it.emails.isNotEmpty() }

    fun toggle(id: String) =
        _state.update { st ->
            st.copy(markedIds = if (id in st.markedIds) st.markedIds - id else st.markedIds + id)
        }

    fun commit(): List<Contact> {
        val marked = _state.value.markedIds
        return contacts.map { c ->
            if (c.id !in marked) {
                c
            } else {
                when (actionFor(c)) {
                    TidyAction.EMAIL_NAME -> CompanyNormalizer.nameFromEmail(c)
                    TidyAction.COMPANY -> CompanyNormalizer.markAsCompany(c)
                }
            }
        }
    }

    /** Default-marked: a high-precision company name, or a nameless card with an email. */
    private fun suggested(c: Contact): Boolean =
        CompanyNameDetector.isHighPrecision(c.name) ||
            (companyNameText(c.name).isBlank() && c.org.isNullOrBlank() && c.emails.isNotEmpty())
}

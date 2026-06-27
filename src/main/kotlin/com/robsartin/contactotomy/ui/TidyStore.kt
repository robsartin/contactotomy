package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.company.CompanyNameDetector
import com.robsartin.contactotomy.core.company.CompanyNormalizer
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TidyState(
    val markedIds: Set<String> = emptySet(),
)

/** Review which standalone contacts are companies; high-precision suspects start marked. */
class TidyStore(
    private val contacts: List<Contact>,
) {
    private val _state =
        MutableStateFlow(
            TidyState(
                markedIds = contacts.filter { CompanyNameDetector.isHighPrecision(it.name) }.map { it.id }.toSet(),
            ),
        )
    val state: StateFlow<TidyState> = _state.asStateFlow()

    /** Contacts with a non-blank name — the only ones worth marking. */
    fun listed(): List<Contact> = contacts.filter { companyNameText(it.name).isNotBlank() }

    fun toggle(id: String) =
        _state.update { st ->
            st.copy(markedIds = if (id in st.markedIds) st.markedIds - id else st.markedIds + id)
        }

    fun commit(): List<Contact> {
        val marked = _state.value.markedIds
        return contacts.map { if (it.id in marked) CompanyNormalizer.markAsCompany(it) else it }
    }
}

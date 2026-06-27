package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source

enum class Screen { IMPORT, MERGE, COMPANIES, DELETION, EXPORT }

data class ImportedFile(
    val path: String,
    val source: Source,
    val count: Int,
    // Stable association to this import's contributed contacts: every contact
    // contributed by this row has an id starting with [prefix]. Lets removal
    // target the right contacts even when the same path is imported twice.
    val prefix: String,
)

data class AppState(
    val screen: Screen = Screen.IMPORT,
    val imported: List<ImportedFile> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
    val mergedContacts: List<Contact>? = null,
    val companyContacts: List<Contact>? = null,
    val finalContacts: List<Contact>? = null,
)

/** The most-processed contact set available: final, else company-reviewed, else merged, else imported. */
fun workingContacts(state: AppState): List<com.robsartin.contactotomy.core.model.Contact> =
    state.finalContacts ?: state.companyContacts ?: state.mergedContacts ?: state.contacts

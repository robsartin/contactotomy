package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source

enum class Screen { IMPORT, MERGE, DELETION, EXPORT }

data class ImportedFile(
    val path: String,
    val source: Source,
    val count: Int,
)

data class AppState(
    val screen: Screen = Screen.IMPORT,
    val imported: List<ImportedFile> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
)

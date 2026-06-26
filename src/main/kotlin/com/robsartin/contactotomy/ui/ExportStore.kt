package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.exporter.VcfExporter
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExportState(
    val contactCount: Int,
    val exportedPath: String? = null,
    val error: String? = null,
)

/** Generates the cleaned vCard for [contacts]; the composable performs the file write. */
class ExportStore(
    private val contacts: List<Contact>,
    private val exporter: VcfExporter = VcfExporter(),
) {
    private val _state = MutableStateFlow(ExportState(contactCount = contacts.size))
    val state: StateFlow<ExportState> = _state.asStateFlow()

    fun vcard(): String = exporter.export(contacts)

    fun recordExported(path: String) = _state.update { it.copy(exportedPath = path, error = null) }

    fun recordError(message: String) = _state.update { it.copy(error = message) }
}

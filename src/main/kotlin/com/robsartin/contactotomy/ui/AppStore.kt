package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Holds the UI state for the import workflow.
 *
 * Single-writer assumption: this store is mutated only from the desktop app's
 * single UI thread/coroutine scope. [importFile] and [removeImportedFile] are
 * not synchronized against concurrent callers by design; interleaved imports
 * would corrupt the monotonic import counter and are not supported.
 */
class AppStore(
    private val parse: (String, Source) -> List<Contact> = { path, source ->
        com.robsartin.contactotomy.core.importer
            .VcfImporter(source)
            .import(java.io.File(path).readText())
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var importCounter = 0

    fun next() {
        val s = _state.value
        if (s.screen == Screen.IMPORT && s.contacts.isEmpty()) return
        val order = Screen.entries
        val i = order.indexOf(s.screen)
        if (i < order.lastIndex) _state.update { it.copy(screen = order[i + 1]) }
    }

    fun back() {
        val order = Screen.entries
        val i = order.indexOf(_state.value.screen)
        if (i > 0) _state.update { it.copy(screen = order[i - 1]) }
    }

    fun goTo(screen: Screen) = _state.update { it.copy(screen = screen) }

    fun setMergedContacts(merged: List<Contact>) = _state.update { it.copy(mergedContacts = merged) }

    fun setTidyContacts(tidy: List<Contact>) = _state.update { it.copy(tidyContacts = tidy) }

    fun setFinalContacts(final: List<Contact>) = _state.update { it.copy(finalContacts = final) }

    suspend fun importFile(
        path: String,
        source: Source,
    ) {
        _state.update { it.copy(importing = true, error = null) }
        val result = runCatching { withContext(ioDispatcher) { parse(path, source) } }
        result
            .onSuccess { parsed ->
                val n = importCounter++
                val prefix = "imp$n:"
                val namespaced = parsed.map { it.copy(id = prefix + it.id) }
                _state.update { st ->
                    st.copy(
                        importing = false,
                        imported = st.imported + ImportedFile(path, source, parsed.size, prefix),
                        contacts = st.contacts + namespaced,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(importing = false, error = e.message ?: "Import failed") }
            }
    }

    fun removeImportedFile(path: String) {
        _state.update { st ->
            // Remove the most-recent summary row for this path and only the
            // contacts contributed by that specific import (its prefix). If the
            // same path was imported more than once, the other imports survive.
            val target = st.imported.lastOrNull { it.path == path } ?: return@update st
            st.copy(
                imported =
                    st.imported.toMutableList().apply {
                        removeAt(indexOfLast { it.path == path })
                    },
                contacts = st.contacts.filterNot { it.id.startsWith(target.prefix) },
            )
        }
    }
}

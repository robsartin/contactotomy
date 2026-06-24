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
    private val prefixByPath = mutableMapOf<String, String>()

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
                prefixByPath[path] = prefix
                val namespaced = parsed.map { it.copy(id = prefix + it.id) }
                _state.update { st ->
                    st.copy(
                        importing = false,
                        imported = st.imported + ImportedFile(path, source, parsed.size),
                        contacts = st.contacts + namespaced,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(importing = false, error = e.message ?: "Import failed") }
            }
    }

    fun removeImportedFile(path: String) {
        val prefix = prefixByPath.remove(path) ?: return
        _state.update { st ->
            st.copy(
                imported = st.imported.filterNot { it.path == path },
                contacts = st.contacts.filterNot { it.id.startsWith(prefix) },
            )
        }
    }
}

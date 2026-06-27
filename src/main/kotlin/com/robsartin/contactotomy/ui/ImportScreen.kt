package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    store: AppStore,
    applePicker: FilePicker = NoopPicker,
    googlePicker: FilePicker = NoopPicker,
    otherPicker: FilePicker = NoopPicker,
) {
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        SectionHeader("Import your vCard exports")
        PickerRow("Choose Apple export") { applePicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.APPLE) } } }
        PickerRow("Choose Google export") { googlePicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.GOOGLE) } } }
        PickerRow("Add another file…") { otherPicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.FILE) } } }

        if (state.importing) Text("Importing…", Modifier.padding(top = 8.dp))
        state.error?.let { Text("Error: $it", Modifier.padding(top = 8.dp)) }

        val total = state.contacts.size
        Text("$total contacts from ${state.imported.size} files", Modifier.padding(vertical = 8.dp))

        state.imported.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${f.path}  [${f.source}]  ${f.count} contacts", Modifier.padding(end = 8.dp))
                Button(onClick = { store.removeImportedFile(f.path) }) { Text("Remove") }
            }
        }
    }
}

private val NoopPicker = FilePicker { null }

@Composable
private fun PickerRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Button(onClick = onClick) { Text(label) }
    }
}

package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

private val NoopPicker = FilePicker { null }

@Composable
fun ExportScreen(
    store: ExportStore,
    savePicker: FilePicker = NoopPicker,
    instructions: String = loadExportInstructions(),
) {
    val state by store.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        com.robsartin.contactotomy.ui.components
            .SectionHeader("Export your cleaned contacts")
        Text("Ready to export ${state.contactCount} cleaned contacts")
        Button(
            onClick = {
                savePicker.pick()?.let { path ->
                    runCatching { File(path).writeText(store.vcard()) }
                        .onSuccess { store.recordExported(path) }
                        .onFailure { store.recordError(it.message ?: "Export failed") }
                }
            },
            modifier = Modifier.padding(vertical = 6.dp),
        ) { Text("Save cleaned vCard…") }

        state.exportedPath?.let { Text("✓ Exported ${state.contactCount} contacts to $it") }
        state.error?.let { Text("Error: $it") }

        Text("How to import your cleaned contacts", Modifier.padding(top = 8.dp))
        androidx.compose.material.Card(
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(com.robsartin.contactotomy.ui.theme.Dimens.cardRadius),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(instructions)
            }
        }
    }
}

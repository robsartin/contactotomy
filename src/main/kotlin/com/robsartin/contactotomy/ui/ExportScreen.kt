package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.theme.Dimens
import java.io.File

private val NoopPicker = FilePicker { null }

internal fun buildSummaryLine(summary: ExportSummary): String {
    val mergedPart = if (summary.merged > 0) "−${summary.merged}" else "0"
    val removedPart = if (summary.removed > 0) "−${summary.removed}" else "0"
    return "Imported ${summary.imported} · merged $mergedPart · removed $removedPart · exporting ${summary.exporting}"
}

@Composable
fun ExportScreen(
    store: ExportStore,
    savePicker: FilePicker = NoopPicker,
    summary: ExportSummary = ExportSummary(0, 0, 0, 0),
    instructions: String = loadExportInstructions(),
) {
    val state by store.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        SectionHeader("Export your cleaned contacts")
        Text("Ready to export ${state.contactCount} cleaned contacts")
        Text(buildSummaryLine(summary), modifier = Modifier.padding(top = Dimens.xs))
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
        Card(
            shape = RoundedCornerShape(Dimens.cardRadius),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(instructions)
            }
        }
    }
}

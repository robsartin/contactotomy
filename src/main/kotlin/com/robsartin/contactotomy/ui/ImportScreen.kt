package com.robsartin.contactotomy.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors
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
    val c = appColors

    Column(Modifier.fillMaxWidth().padding(top = Dimens.sm)) {
        SectionHeader("Import your vCard exports")
        PickerRow("Choose Apple export") { applePicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.APPLE) } } }
        PickerRow("Choose Google export") { googlePicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.GOOGLE) } } }
        PickerRow("Add another file…") { otherPicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.FILE) } } }

        if (state.importing) Text("Importing…", Modifier.padding(top = Dimens.sm))
        state.error?.let { Text("Error: $it", Modifier.padding(top = Dimens.sm), color = c.reject) }

        val total = state.contacts.size
        Text(
            "$total contacts from ${state.imported.size} files",
            Modifier.padding(vertical = Dimens.sm),
            color = c.muted,
        )

        if (state.imported.isEmpty()) {
            Text(
                "No files imported yet — use the buttons above to add your vCard exports.",
                Modifier.padding(vertical = Dimens.sm),
                color = c.muted,
            )
        } else {
            val scrollState = rememberScrollState()
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                    state.imported.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = Dimens.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${f.path}  [${f.source}]  ${f.count} contacts",
                                Modifier.weight(1f).padding(end = Dimens.sm),
                            )
                            Button(onClick = { store.removeImportedFile(f.path) }) { Text("Remove") }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState),
                )
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
    Row(Modifier.fillMaxWidth().padding(vertical = Dimens.xs)) {
        Button(onClick = onClick) { Text(label) }
    }
}

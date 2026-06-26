package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun App(
    store: AppStore,
    applePicker: FilePicker = AwtFilePicker("Choose Apple vCard export"),
    googlePicker: FilePicker = AwtFilePicker("Choose Google vCard export"),
    otherPicker: FilePicker = AwtFilePicker("Choose a vCard file"),
) {
    val state: AppState by store.state.collectAsState()
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            StepIndicator(state.screen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { store.back() }, enabled = state.screen != Screen.IMPORT) { Text("Back") }
                // Next only advances from Import. Merge and Deletion must advance via their own
                // "Apply … & continue" buttons, which commit the result — otherwise the global Next
                // would skip the commit and the merge/deletion would silently not be applied.
                val nextEnabled = state.screen == Screen.IMPORT && state.contacts.isNotEmpty()
                Button(onClick = { store.next() }, enabled = nextEnabled) { Text("Next") }
            }
            when (state.screen) {
                Screen.IMPORT ->
                    ImportScreen(store, applePicker, googlePicker, otherPicker)
                Screen.MERGE -> {
                    val reviewStore =
                        androidx.compose.runtime.remember(state.contacts) { MergeReviewStore(state.contacts) }
                    MergeScreen(reviewStore) { merged ->
                        store.setMergedContacts(merged)
                        store.next()
                    }
                }
                Screen.DELETION -> {
                    val source = state.mergedContacts ?: state.contacts
                    val deletionStore = androidx.compose.runtime.remember(source) { DeletionReviewStore(source) }
                    DeletionScreen(
                        deletionStore,
                        loadPicker = AwtFilePicker("Load rules (.json)"),
                        savePicker = AwtFilePicker("Save rules (.json)", save = true),
                    ) { final ->
                        store.setFinalContacts(final)
                        store.next()
                    }
                }
                Screen.EXPORT -> {
                    val exportStore =
                        androidx.compose.runtime.remember(workingContacts(state)) {
                            ExportStore(workingContacts(state))
                        }
                    ExportScreen(
                        exportStore,
                        savePicker =
                            AwtFilePicker("Save cleaned vCard (.vcf)", save = true, defaultName = "contacts-clean.vcf"),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Screen) {
    val labels =
        listOf(
            Screen.IMPORT to "Import",
            Screen.MERGE to "Merge",
            Screen.DELETION to "Deletion",
            Screen.EXPORT to "Export",
        )
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        labels.forEach { (screen, label) ->
            if (screen == current) Text("▸")
            Text(label)
        }
    }
}

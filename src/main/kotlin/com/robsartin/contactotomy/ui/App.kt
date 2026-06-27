package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.ui.theme.ContactotomyTheme

@Composable
fun App(
    store: AppStore,
    applePicker: FilePicker = AwtFilePicker("Choose Apple vCard export"),
    googlePicker: FilePicker = AwtFilePicker("Choose Google vCard export"),
    otherPicker: FilePicker = AwtFilePicker("Choose a vCard file"),
) {
    val state: AppState by store.state.collectAsState()
    ContactotomyTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            StepIndicator(state.screen)

            // Hoist the per-screen stores so the single top Next can commit the current
            // screen before advancing, replacing the old per-screen commit buttons.
            val mergeStore = remember(state.contacts) { MergeReviewStore(state.contacts) }
            val tidySource = state.mergedContacts ?: state.contacts
            val tidyStore = remember(tidySource) { TidyStore(tidySource) }
            val deletionSource = state.tidyContacts ?: state.mergedContacts ?: state.contacts
            val deletionStore = remember(deletionSource) { DeletionReviewStore(deletionSource) }
            val exportSource = workingContacts(state)
            val exportStore = remember(exportSource) { ExportStore(exportSource) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { store.back() }, enabled = state.screen != Screen.IMPORT) { Text("Back") }
                val nextEnabled =
                    when (state.screen) {
                        Screen.EXPORT -> false
                        Screen.IMPORT -> state.contacts.isNotEmpty()
                        else -> true
                    }
                Button(
                    onClick = {
                        when (state.screen) {
                            Screen.MERGE -> {
                                store.setMergedContacts(mergeStore.commit())
                                store.next()
                            }
                            Screen.TIDY -> {
                                store.setTidyContacts(tidyStore.commit())
                                store.next()
                            }
                            Screen.DELETION -> {
                                store.setFinalContacts(deletionStore.commit())
                                store.next()
                            }
                            else -> store.next()
                        }
                    },
                    enabled = nextEnabled,
                ) { Text("Next") }
            }
            when (state.screen) {
                Screen.IMPORT ->
                    ImportScreen(store, applePicker, googlePicker, otherPicker)
                Screen.MERGE ->
                    MergeScreen(mergeStore)
                Screen.TIDY ->
                    TidyScreen(tidyStore)
                Screen.DELETION ->
                    DeletionScreen(
                        deletionStore,
                        loadPicker = AwtFilePicker("Load rules (.json)"),
                        savePicker = AwtFilePicker("Save rules (.json)", save = true),
                    )
                Screen.EXPORT ->
                    ExportScreen(
                        exportStore,
                        savePicker =
                            AwtFilePicker("Save cleaned vCard (.vcf)", save = true, defaultName = "contacts-clean.vcf"),
                    )
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
            Screen.TIDY to "Tidy",
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

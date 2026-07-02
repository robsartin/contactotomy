package com.robsartin.contactotomy.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.components.SourceBadge
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

/**
 * Two-section Review screen.
 *
 * - Section 1: "Duplicates to merge" — reuses the MergeScreen composable body
 *   driven by [store.mergeStore].
 * - Section 2: "Cards to clean" — singleton suggested contacts with checkbox
 *   toggle, SourceBadge, and tidy hint (→ org / → name).
 *
 * Layout: Column with Section 1 taking weight(1f) when it has items, Section 2
 * also taking weight(1f) when it has items, so both scroll independently.
 */
@Composable
fun ReviewScreen(store: ReviewStore) {
    val mergeState by store.mergeStore.state.collectAsState()
    // Section 2 marked state, collected from the store's StateFlow.
    val marked by store.markedState.collectAsState()
    // Lift picker state to ReviewScreen level so the picker overlay spans the full screen,
    // preventing ambiguous node matches when Section 2 also shows merge-eligible contacts.
    var showPicker by remember { mutableStateOf(false) }

    val hasMergeItems = mergeState.items.isNotEmpty()
    val candidates = store.cleanCandidates()
    val hasCleanItems = candidates.isNotEmpty()

    // When the manual-merge picker is open, render it full-screen over both sections.
    if (showPicker) {
        MergeScreen(
            store = store.mergeStore,
            externalShowPicker = true,
            onExternalPickerChange = { showPicker = it },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Section 1: Duplicates to merge ----
        SectionHeader("Duplicates to merge")
        // Always render MergeScreen so the "+ Manual merge" button is always accessible.
        // Show "No duplicates found" as a hint when there are no auto-detected items;
        // the MergeScreen itself handles the empty list gracefully.
        if (!hasMergeItems) {
            Text(
                "No duplicates found",
                Modifier.padding(vertical = 4.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MergeScreen(
                store = store.mergeStore,
                externalShowPicker = false,
                onExternalPickerChange = { v -> showPicker = v },
            )
        }

        // ---- Section 2: Cards to clean ----
        SectionHeader("Cards to clean")
        if (!hasCleanItems) {
            Text(
                "No single cards need cleaning",
                Modifier.padding(vertical = 8.dp),
            )
        } else {
            CleanSection(
                candidates = candidates,
                store = store,
                marked = marked,
                onToggle = { id -> store.toggleClean(id) },
                // Always give Section 2 a weight so it doesn't consume all remaining height
                // and leave Section 1 with too little space for scrollable content.
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CleanSection(
    candidates: List<Contact>,
    store: ReviewStore,
    marked: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }

    Row(modifier.fillMaxWidth().fillMaxHeight()) {
        // Left: clean candidate list
        Column(Modifier.weight(1f)) {
            val cleanListState = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(state = cleanListState, modifier = Modifier.fillMaxHeight()) {
                    items(candidates) { c ->
                        val isMarked = c.id in marked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedId = c.id }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isMarked,
                                onCheckedChange = { onToggle(c.id) },
                                modifier = Modifier.testTag("mark:${c.id}"),
                            )
                            SourceBadge(c.source)
                            Text("  ${displayName(c.name).ifBlank { "(no name)" }}")
                            if (isMarked) {
                                val hint =
                                    when (store.actionFor(c)) {
                                        TidyAction.EMAIL_NAME -> "→ name: ${c.emails.first()}"
                                        TidyAction.PHONE_NAME -> "→ name: ${c.phones.first()}"
                                        TidyAction.COMPANY -> "→ org: ${companyNameText(c.name)}"
                                    }
                                Text(
                                    "  $hint",
                                    color = appColors.muted,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(cleanListState),
                )
            }
        }
        // Right: detail pane
        Column(Modifier.fillMaxWidth(0.42f).padding(start = 12.dp)) {
            val selected = candidates.firstOrNull { it.id == selectedId }
            if (selected == null) {
                Text("Select a card")
            } else {
                Card(shape = RoundedCornerShape(Dimens.cardRadius)) {
                    Box(Modifier.padding(10.dp)) {
                        Column {
                            Text(displayName(selected.name))
                            if (selected.emails.isNotEmpty()) Text(selected.emails.joinToString())
                            if (selected.phones.isNotEmpty()) Text(selected.phones.joinToString())
                            selected.org?.let { Text(it) }
                            Text("source: ${selected.source}")
                        }
                    }
                }
            }
        }
    }
}

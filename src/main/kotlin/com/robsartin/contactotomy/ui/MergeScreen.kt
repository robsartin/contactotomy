package com.robsartin.contactotomy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName

@Composable
fun MergeScreen(store: MergeReviewStore) {
    val state by store.state.collectAsState()
    val pending = state.items.filter { it.decision == Decision.PENDING }
    val resolved = state.items.filter { it.decision != Decision.PENDING }
    val willMerge = state.items.count { it.decision == Decision.ACCEPT }

    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.items.firstOrNull { it.id == selectedId } ?: pending.firstOrNull()

    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        ManualMergePicker(
            eligible = store.eligibleForManualMerge(),
            onCancel = { showPicker = false },
            onCreate = { ids ->
                store.manualMerge(ids)?.let { selectedId = it }
                showPicker = false
            },
        )
        return
    }

    Row(Modifier.fillMaxWidth().fillMaxHeight().padding(top = 8.dp)) {
        // ---- LEFT: review list ----
        Column(Modifier.fillMaxWidth(0.42f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Needs review (${pending.size})")
                Row {
                    Button(onClick = { showPicker = true }) { Text("+ Manual merge") }
                    if (pending.any { it.origin == Origin.HIGH }) {
                        Button(onClick = { store.acceptAllHighConfidence() }) { Text("Accept all high-confidence") }
                    }
                }
            }
            Text("${resolved.size} of ${state.items.size} reviewed", Modifier.padding(vertical = 4.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(pending) { item ->
                    ClusterRow(item, selected = item.id == (selected?.id)) { selectedId = item.id }
                }
                if (resolved.isNotEmpty()) {
                    item { Text("Resolved (${resolved.size})", Modifier.padding(top = 10.dp)) }
                    items(resolved) { item -> ResolvedRow(item) { store.undo(item.id) } }
                }
            }
            Text(
                "Will merge $willMerge clusters · ${pending.size} still pending",
                Modifier.padding(top = 6.dp),
            )
        }
        // ---- RIGHT: detail ----
        Column(Modifier.fillMaxWidth().fillMaxHeight().padding(start = 12.dp)) {
            if (selected == null) {
                Text("All clusters reviewed")
            } else {
                // Scrollable content: source cards + merged-result fields.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    MergeDetailContent(store, selected)
                }
                // Pinned footer: decision buttons stay on-screen regardless of detail length.
                Row(Modifier.padding(top = 10.dp)) {
                    Button(onClick = {
                        store.accept(selected.id)
                        selectedId = pending.firstOrNull { it.id != selected.id }?.id
                    }) { Text("✓ Accept merge") }
                    Button(onClick = {
                        store.reject(selected.id)
                        selectedId = pending.firstOrNull { it.id != selected.id }?.id
                    }) { Text("✕ Keep separate") }
                }
            }
        }
    }
}

@Composable
private fun ClusterRow(
    item: ReviewItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tag =
        when (item.origin) {
            Origin.UNCERTAIN -> "maybe"
            Origin.MANUAL -> "manual"
            Origin.HIGH -> "HIGH"
        }
    val label =
        if (item.origin == Origin.UNCERTAIN) {
            val names =
                item.proposal.cluster.members
                    .joinToString(" ↔ ") { displayName(it.name) }
            "$names · ${item.proposal.cluster.reasons.joinToString(", ")}"
        } else {
            "${displayName(item.proposal.merged.name)} · ${item.proposal.cluster.members.size} cards"
        }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Button(onClick = onClick) { Text((if (selected) "▸ " else "") + "$label  [$tag]") }
    }
}

@Composable
private fun ResolvedRow(
    item: ReviewItem,
    onUndo: () -> Unit,
) {
    val mark = if (item.decision == Decision.ACCEPT) "✓ will merge" else "✕ kept separate"
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$mark · ${displayName(item.proposal.merged.name)}")
        Button(onClick = onUndo) { Text("Undo") }
    }
}

@Composable
private fun MergeDetailContent(
    store: MergeReviewStore,
    item: ReviewItem,
) {
    val p = item.proposal
    Column {
        SourceCards(p.cluster.members)

        Text("Merged result — tick what to keep", Modifier.padding(top = 8.dp, bottom = 4.dp))

        // Name: pick which source card's name wins (most-complete pre-selected = merged.name).
        val names =
            p.cluster.members
                .associate { it.id to displayName(it.name) }
                .filterValues { it.isNotBlank() }
        if (names.isNotEmpty()) {
            Text("Name (pick one)")
            names.forEach { (memberId, name) ->
                val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = memberId == chosen, onClick = { store.chooseName(item.id, memberId) })
                    Text(name)
                }
            }
        }

        // Multi-value fields: include/exclude each value with a checkbox.
        MultiField("phones", p.merged.phones, item, store)
        MultiField("emails", p.merged.emails, item, store)
        MultiField("categories", p.merged.categories, item, store)

        // Single-value conflicts (org/title/notes): pick one with a radio.
        p.conflicts.forEach { conflict ->
            Text("${conflict.field} (pick one)", Modifier.padding(top = 4.dp))
            conflict.candidates.map { it.value }.distinct().forEach { value ->
                val chosen = item.conflictChoices[conflict.field] ?: conflict.chosen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = value == chosen,
                        onClick = { store.chooseConflict(item.id, conflict.field, value) },
                    )
                    Text(value)
                }
            }
        }
    }
}

@Composable
private fun SourceCards(members: List<Contact>) {
    val sorted = members.sortedWith(compareByDescending(nullsLast()) { it.modifiedAt })
    Text("Source cards (${sorted.size})", Modifier.padding(bottom = 4.dp))
    sorted.forEachIndexed { index, m ->
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            val primary = if (index == 0) " ★ primary" else ""
            val date = m.modifiedAt?.toString()?.take(10) ?: "—"
            Text("[${m.source}]$primary · $date")
            Text(displayName(m.name))
            val line = (m.phones + m.emails + listOfNotNull(m.org)).joinToString(" · ")
            if (line.isNotEmpty()) Text(line)
        }
    }
}

@Composable
private fun MultiField(
    field: String,
    values: List<String>,
    item: ReviewItem,
    store: MergeReviewStore,
) {
    if (values.isEmpty()) return
    Text("$field (keep any)", Modifier.padding(top = 4.dp))
    values.forEach { value ->
        val ev = ExcludedValue(field, value)
        val included = ev !in item.excludedValues
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = included, onCheckedChange = { store.toggleField(item.id, ev) })
            Text(value)
        }
    }
}

@Composable
private fun ManualMergePicker(
    eligible: List<Contact>,
    onCancel: () -> Unit,
    onCreate: (List<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<String>() }
    val filtered =
        eligible.filter { c ->
            query.isBlank() ||
                (displayName(c.name) + " " + c.phones.joinToString(" ") + " " + c.emails.joinToString(" "))
                    .contains(query, ignoreCase = true)
        }
    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(8.dp)) {
        Text("Manual merge — pick two or more cards")
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") })
        Text("Selected (${selected.size})", Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(filtered) { c ->
                val isSel = c.id in selected
                Row(
                    Modifier.fillMaxWidth().clickable { if (isSel) selected.remove(c.id) else selected.add(c.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = isSel, onCheckedChange = null)
                    Text("${displayName(c.name)}  [${c.source}]")
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { onCreate(selected.toList()) }, enabled = selected.size >= 2) { Text("Create merge") }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun defaultNameMemberId(members: List<Contact>): String =
    members
        .maxByOrNull { m ->
            listOf(m.name.prefix, m.name.given, m.name.middle, m.name.family, m.name.suffix).count { p -> !p.isNullOrBlank() }
        }?.id
        ?: members.first().id

private fun displayName(name: ContactName): String = name.formatted ?: listOfNotNull(name.given, name.family).joinToString(" ")

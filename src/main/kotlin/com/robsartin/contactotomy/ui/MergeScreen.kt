package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.model.Contact

@Composable
fun MergeScreen(
    store: MergeReviewStore,
    onCommit: (List<Contact>) -> Unit,
) {
    val state by store.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    val high = state.items.filter { it.origin == Origin.HIGH }
    val uncertain = state.items.filter { it.origin == Origin.UNCERTAIN }
    val willMerge = state.items.count { it.decision == Decision.ACCEPT }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth(0.45f)) {
            Text("Will merge $willMerge clusters", Modifier.padding(bottom = 6.dp))
            if (high.isNotEmpty()) {
                Row {
                    Text("To merge (${high.size})")
                    Button(onClick = { store.acceptAllHighConfidence() }) { Text("Accept all") }
                }
            }
            LazyColumn {
                items(high) { item -> ClusterRow(item) { selectedId = item.id } }
                if (uncertain.isNotEmpty()) {
                    item { Text("Possible matches (${uncertain.size})", Modifier.padding(top = 8.dp)) }
                }
                items(uncertain) { item -> ClusterRow(item) { selectedId = item.id } }
            }
            Button(onClick = { onCommit(store.commit()) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Apply merges & continue")
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            val selected = state.items.firstOrNull { it.id == selectedId }
            if (selected == null) Text("Select a cluster to review") else MergeDetail(store, selected)
        }
    }
}

@Composable
private fun ClusterRow(
    item: ReviewItem,
    onClick: () -> Unit,
) {
    val mark =
        when (item.decision) {
            Decision.ACCEPT -> "✓"
            Decision.REJECT -> "✕"
            Decision.SKIP -> "◷"
        }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Button(onClick = onClick) {
            val name =
                item.proposal.merged.name
                    .let { it.formatted ?: listOfNotNull(it.given, it.family).joinToString(" ") }
            Text("$mark  $name · ${item.proposal.cluster.members.size} cards")
        }
    }
}

@Composable
private fun MergeDetail(
    store: MergeReviewStore,
    item: ReviewItem,
) {
    val p = item.proposal
    Column(Modifier.padding(start = 4.dp)) {
        val name =
            p.merged.name
                .let { it.formatted ?: listOfNotNull(it.given, it.family).joinToString(" ") }
        Text("Merged: $name", Modifier.padding(bottom = 6.dp))

        // multi-value fields as include/exclude chips
        MultiField("phones", p.merged.phones, item, store)
        MultiField("emails", p.merged.emails, item, store)
        MultiField("categories", p.merged.categories, item, store)

        // single-value conflicts as choices
        p.conflicts.forEach { conflict ->
            Text("${conflict.field} (conflict):", Modifier.padding(top = 6.dp))
            Row {
                conflict.candidates.map { it.value }.distinct().forEach { value ->
                    val chosen = item.conflictChoices[conflict.field] ?: conflict.chosen
                    val mark = if (value == chosen) "◉" else "○"
                    Button(onClick = { store.chooseConflict(item.id, conflict.field, value) }) { Text("$mark $value") }
                }
            }
        }

        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { store.setDecision(item.id, Decision.ACCEPT) }) { Text("Accept") }
            Button(onClick = { store.setDecision(item.id, Decision.REJECT) }) { Text("Reject") }
            Button(onClick = { store.setDecision(item.id, Decision.SKIP) }) { Text("Skip") }
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
    Text(field, Modifier.padding(top = 4.dp))
    Row {
        values.forEach { value ->
            val ev =
                com.robsartin.contactotomy.core.apply
                    .ExcludedValue(field, value)
            val included = ev !in item.excludedValues
            val mark = if (included) "☑" else "☐"
            Button(onClick = { store.toggleField(item.id, ev) }) { Text("$mark $value") }
        }
    }
}

package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.rules.Flagged
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.theme.Dimens
import java.io.File

private val NoopPicker = FilePicker { null }

@Composable
fun DeletionScreen(
    store: DeletionReviewStore,
    loadPicker: FilePicker = NoopPicker,
    savePicker: FilePicker = NoopPicker,
) {
    val state by store.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    // Rule-builder dialog state: null = closed; non-null Rule = editing that rule; "" name = new
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var showNewRule by remember { mutableStateOf(false) }

    // Show dialog when either new or edit is active
    if (showNewRule || editingRule != null) {
        val builderStore =
            remember(showNewRule, editingRule) {
                RuleBuilderStore(store.contactList, existing = editingRule)
            }
        RuleBuilderDialog(
            store = builderStore,
            onSave = { rule ->
                if (editingRule != null) {
                    store.updateRule(editingRule!!.name, rule)
                } else {
                    store.addRule(rule)
                }
                editingRule = null
                showNewRule = false
            },
            onCancel = {
                editingRule = null
                showNewRule = false
            },
        )
    }

    // flagged grouped by matched rule name
    val byRule: Map<String, List<Flagged>> =
        state.flagged
            .flatMap { f -> f.matches.map { it.ruleName to f } }
            .groupBy({ it.first }, { it.second })

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            // --- Left: rules ---
            Column(Modifier.fillMaxWidth(0.28f).padding(end = 8.dp)) {
                SectionHeader("Rules")
                state.rules.forEach { rt ->
                    Row {
                        Checkbox(checked = rt.enabled, onCheckedChange = { store.toggleRule(rt.rule.name) })
                        Text(rt.rule.name, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { editingRule = rt.rule },
                            modifier = Modifier.testTag("edit-rule:${rt.rule.name}"),
                        ) { Text("Edit") }
                        TextButton(
                            onClick = { store.removeRule(rt.rule.name) },
                            modifier = Modifier.testTag("delete-rule:${rt.rule.name}"),
                        ) { Text("✕") }
                    }
                }
                Button(
                    onClick = { showNewRule = true },
                    modifier = Modifier.testTag("new-rule"),
                ) { Text("New rule…") }
                Row(Modifier.padding(top = 6.dp)) {
                    Button(onClick = { loadPicker.pick()?.let { store.loadRules(File(it).readText()) } }) { Text("Load…") }
                    Button(onClick = { savePicker.pick()?.let { File(it).writeText(store.rulesToJson()) } }) { Text("Save…") }
                    Button(onClick = { store.run() }) { Text("Run") }
                }
            }
            // --- Middle: flagged grouped by rule ---
            Column(Modifier.fillMaxWidth(0.55f).padding(end = 8.dp)) {
                if (!state.hasRun) {
                    Text("Run rules to see matches")
                } else if (byRule.isEmpty()) {
                    Text("No matches")
                } else {
                    Button(onClick = { store.approveAll() }) { Text("Approve all") }
                    LazyColumn {
                        byRule.forEach { (ruleName, flaggeds) ->
                            item {
                                Row {
                                    Text("$ruleName (${flaggeds.size})")
                                    Button(onClick = { store.approveAllForRule(ruleName) }) { Text("Approve all") }
                                }
                            }
                            items(flaggeds.size) { i ->
                                val f = flaggeds[i]
                                val approved = f.contact.id in state.approvedIds
                                Row(Modifier.fillMaxWidth()) {
                                    Checkbox(
                                        checked = approved,
                                        onCheckedChange = { if (it) store.approve(f.contact.id) else store.unapprove(f.contact.id) },
                                    )
                                    Button(onClick = { selectedId = f.contact.id }) {
                                        Text(displayName(f.contact) + " · " + f.matches.joinToString { it.reason })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // --- Right: card detail ---
            Column {
                val selected = state.flagged.firstOrNull { it.contact.id == selectedId }
                if (selected == null) {
                    Text("Select a flagged contact")
                } else {
                    Card(shape = RoundedCornerShape(Dimens.cardRadius)) {
                        Box(Modifier.padding(10.dp)) { CardDetail(selected) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                "${state.flagged.size} flagged · ${state.approvedIds.size} approved → " +
                    "${state.totalContacts - state.approvedIds.size} remain",
            )
        }
    }
}

@Composable
private fun CardDetail(f: Flagged) {
    val c = f.contact
    Column {
        Text(displayName(c))
        if (c.emails.isNotEmpty()) Text(c.emails.joinToString())
        if (c.phones.isNotEmpty()) Text(c.phones.joinToString())
        c.org?.let { Text(it) }
        Text("source: ${c.source}")
        Text("flagged: " + f.matches.joinToString { it.reason })
    }
}

private fun displayName(c: Contact): String =
    c.name.formatted ?: listOfNotNull(c.name.given, c.name.family).joinToString(" ").ifBlank { "(no name)" }

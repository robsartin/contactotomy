package com.robsartin.contactotomy.ui

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.core.rules.Flagged
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.components.SourceBadge
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
        Row(Modifier.fillMaxWidth().weight(1f)) {
            // --- Left: rules (with scrollbar) ---
            Box(Modifier.fillMaxWidth(0.28f).fillMaxHeight().padding(end = 8.dp)) {
                val rulesScrollState = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(rulesScrollState)) {
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
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(rulesScrollState),
                )
            }
            // --- Middle: flagged grouped by rule (with scrollbar) ---
            Box(Modifier.fillMaxWidth(0.55f).fillMaxHeight().padding(end = 8.dp)) {
                if (!state.hasRun) {
                    Text("Run rules to see matches")
                } else if (byRule.isEmpty()) {
                    Text("No matches")
                } else {
                    val flaggedListState = rememberLazyListState()
                    Column(Modifier.fillMaxSize()) {
                        if (state.hasRun) {
                            val summary = deletionPreviewSummary(state)
                            Text(
                                "${summary.flagged} flagged · ${summary.approved} approved for deletion · ${summary.remaining} will remain",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("deletion-summary-headline").padding(bottom = 4.dp),
                            )
                        }
                        Button(onClick = { store.approveAll() }) { Text("Approve all") }
                        Box(Modifier.weight(1f)) {
                            LazyColumn(state = flaggedListState, modifier = Modifier.fillMaxSize()) {
                                byRule.forEach { (ruleName, flaggeds) ->
                                    item {
                                        Row {
                                            Text("$ruleName (${flaggeds.size})")
                                            Button(onClick = { store.approveAllForRule(ruleName) }) { Text("Approve all") }
                                        }
                                    }
                                    items(flaggeds) { f ->
                                        val approved = f.contact.id in state.approvedIds
                                        Row(Modifier.fillMaxWidth()) {
                                            Checkbox(
                                                checked = approved,
                                                onCheckedChange = {
                                                    if (it) store.approve(f.contact.id) else store.unapprove(f.contact.id)
                                                },
                                            )
                                            Button(onClick = { selectedId = f.contact.id }) {
                                                Text(displayName(f.contact) + " · " + f.matches.joinToString { it.reason })
                                            }
                                        }
                                    }
                                }
                            }
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(flaggedListState),
                            )
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
    val scrollState = rememberScrollState()
    Box {
        Column(Modifier.verticalScroll(scrollState)) {
            Text(displayName(c), fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(c.source)
                Text("  source: ${c.source}", fontSize = 11.sp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
            }
            if (c.emails.isNotEmpty()) {
                CardDetailField("Email", c.emails.joinToString())
            }
            if (c.phones.isNotEmpty()) {
                CardDetailField("Phone", c.phones.joinToString())
            }
            c.org?.let { CardDetailField("Org", it) }
            c.title?.let { CardDetailField("Title", it) }
            if (c.addresses.isNotEmpty()) {
                Column(Modifier.testTag("card-detail-addresses").padding(top = Dimens.xs)) {
                    CardDetailLabel("Address")
                    c.addresses.forEach { addr -> Text(addr.toDisplayString(), fontSize = 12.sp) }
                }
            }
            if (c.urls.isNotEmpty()) {
                Column(Modifier.testTag("card-detail-urls").padding(top = Dimens.xs)) {
                    CardDetailLabel("URL")
                    c.urls.forEach { url -> Text(url, fontSize = 12.sp) }
                }
            }
            c.notes?.let { notes ->
                Column(Modifier.testTag("card-detail-notes").padding(top = Dimens.xs)) {
                    CardDetailLabel("Notes")
                    Text(notes, fontSize = 12.sp)
                }
            }
            if (c.categories.isNotEmpty()) {
                Column(Modifier.testTag("card-detail-categories").padding(top = Dimens.xs)) {
                    CardDetailLabel("Categories")
                    Text(c.categories.joinToString(), fontSize = 12.sp)
                }
            }
            if (c.photo != null) {
                Text(
                    "📷 Has photo",
                    fontSize = 11.sp,
                    modifier = Modifier.testTag("card-detail-has-photo").padding(top = Dimens.xs),
                )
            }
            CardDetailField("flagged", f.matches.joinToString { it.reason })
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState),
        )
    }
}

@Composable
private fun CardDetailField(
    label: String,
    value: String,
) {
    Row(Modifier.padding(top = Dimens.xs)) {
        Text("$label: ", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 12.sp)
    }
}

@Composable
private fun CardDetailLabel(label: String) {
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
}

private fun displayName(c: Contact): String =
    c.name.formatted ?: listOfNotNull(c.name.given, c.name.family).joinToString(" ").ifBlank { "(no name)" }

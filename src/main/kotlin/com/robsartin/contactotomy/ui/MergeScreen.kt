package com.robsartin.contactotomy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.company.CompanyNameDetector
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.ui.components.ClusterRow
import com.robsartin.contactotomy.ui.components.FieldGroup
import com.robsartin.contactotomy.ui.components.LabeledProgress
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.components.SourceCard
import com.robsartin.contactotomy.ui.components.ValuePill
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

/**
 * [MergeScreen] with optional external picker-state control.
 *
 * When [externalShowPicker] and [onExternalPickerChange] are provided (non-null),
 * the picker open/close is driven by the caller (e.g. ReviewScreen) so the picker
 * overlay can span the whole parent rather than just the MergeScreen area.
 * When both are null, picker state is managed internally (backward-compatible).
 */
@Composable
fun MergeScreen(
    store: MergeReviewStore,
    externalShowPicker: Boolean? = null,
    onExternalPickerChange: ((Boolean) -> Unit)? = null,
) {
    val state by store.state.collectAsState()
    val pending = state.items.filter { it.decision == Decision.PENDING }
    val resolved = state.items.filter { it.decision != Decision.PENDING }
    val willMerge = state.items.count { it.decision == Decision.ACCEPT }

    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.items.firstOrNull { it.id == selectedId } ?: pending.firstOrNull()

    val useExternalPicker = externalShowPicker != null && onExternalPickerChange != null
    var internalShowPicker by remember { mutableStateOf(false) }
    val showPicker = if (useExternalPicker) externalShowPicker!! else internalShowPicker
    val setShowPicker: (Boolean) -> Unit = if (useExternalPicker) onExternalPickerChange!! else { v -> internalShowPicker = v }

    val eligible = remember(state) { store.eligibleForManualMerge() }

    if (showPicker) {
        ManualMergePicker(
            eligible = eligible,
            onCancel = { setShowPicker(false) },
            onCreate = { ids ->
                store.manualMerge(ids)?.let { selectedId = it }
                setShowPicker(false)
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
                    Button(onClick = { setShowPicker(true) }) { Text("+ Manual merge") }
                    if (pending.any { it.origin == Origin.HIGH }) {
                        Button(onClick = { store.acceptAllHighConfidence() }) { Text("Accept all high-confidence") }
                    }
                }
            }
            LabeledProgress(reviewed = resolved.size, total = state.items.size)
            LazyColumn(Modifier.weight(1f)) {
                items(pending) { item ->
                    val label =
                        if (item.origin == Origin.UNCERTAIN) {
                            item.proposal.cluster.members
                                .joinToString(" ↔ ") { displayName(it.name) } +
                                " · " +
                                item.proposal.cluster.reasons
                                    .joinToString(", ")
                        } else {
                            "${displayName(item.proposal.merged.name)} · ${item.proposal.cluster.members.size} cards"
                        }
                    ClusterRow(
                        title = label,
                        origin = item.origin,
                        selected = item.id == selected?.id,
                        onClick = { selectedId = item.id },
                    )
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
                    Button(
                        onClick = {
                            store.accept(selected.id)
                            selectedId = pending.firstOrNull { it.id != selected.id }?.id
                        },
                        colors =
                            androidx.compose.material.ButtonDefaults.buttonColors(
                                backgroundColor = appColors.accept,
                                contentColor = androidx.compose.ui.graphics.Color.White,
                            ),
                    ) { Text("✓ Accept merge") }
                    androidx.compose.material.OutlinedButton(
                        onClick = {
                            store.reject(selected.id)
                            selectedId = pending.firstOrNull { it.id != selected.id }?.id
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, appColors.reject),
                        colors =
                            androidx.compose.material.ButtonDefaults
                                .outlinedButtonColors(contentColor = appColors.reject),
                    ) { Text("✕ Keep separate") }
                    androidx.compose.material.OutlinedButton(
                        onClick = {
                            store.deleteItem(selected.id)
                            selectedId = null
                        },
                    ) { Text("🗑 Delete") }
                }
            }
        }
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

        androidx.compose.material.Card(
            border = androidx.compose.foundation.BorderStroke(com.robsartin.contactotomy.ui.theme.Dimens.selected, appColors.mergedBorder),
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(com.robsartin.contactotomy.ui.theme.Dimens.cardRadius),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(Modifier.padding(com.robsartin.contactotomy.ui.theme.Dimens.md)) {
                Text("Merged result — tick what to keep", Modifier.padding(top = 8.dp, bottom = 4.dp))

                // Name: pick which source card's name wins; company-like names are badged.
                val namedMembers = p.cluster.members.filter { displayName(it.name).isNotBlank() }
                if (namedMembers.isNotEmpty()) {
                    FieldGroup("Name (pick one)") {
                        val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                        namedMembers.forEach { m ->
                            val badge = if (CompanyNameDetector.detect(m.name) != null) "  · looks like a company" else ""
                            RadioRow(
                                label = displayName(m.name) + badge,
                                selected = !item.nameCleared && m.id == chosen,
                                onClick = { store.chooseName(item.id, m.id) },
                            )
                        }
                        RadioRow(label = "(no name)", selected = item.nameCleared, onClick = { store.clearName(item.id) })
                    }
                }

                // Multi-value fields: include/exclude each value with a checkbox.
                MultiField("phones", p.merged.phones, item, store)
                MultiField("emails", p.merged.emails, item, store)
                MultiField("urls", p.merged.urls, item, store)
                MultiField("addresses", p.merged.addresses.map { it.toDisplayString() }, item, store)
                MultiField("categories", p.merged.categories, item, store)

                // Company / org has its own control (supports promoting a mis-filed company name).
                CompanyOrgField(store, item)

                // Single-value conflicts (title/notes): pick one with a radio. (org handled above.)
                p.conflicts.filter { it.field != "org" }.forEach { conflict ->
                    FieldGroup("${conflict.field} (pick one)") {
                        val chosen = item.conflictChoices[conflict.field] ?: conflict.chosen
                        val cleared = conflict.field in item.clearedConflicts
                        conflict.candidates.map { it.value }.distinct().forEach { value ->
                            RadioRow(
                                label = value,
                                selected = !cleared && value == chosen,
                                onClick = { store.chooseConflict(item.id, conflict.field, value) },
                            )
                        }
                        RadioRow(
                            label = "(clear)",
                            selected = cleared,
                            onClick = { store.clearConflict(item.id, conflict.field) },
                        )
                    }
                }

                // ---- Freeform edit block ----
                EditOverrideBlock(store, item)
            }
        }
    }
}

/**
 * Editable name-components, org, and notes block layered below the existing
 * pick/exclude controls.  Typing any field sets an override that wins at commit time.
 * Pre-fill from the item's current effective values (what commit() would currently produce).
 */
@Composable
private fun EditOverrideBlock(
    store: MergeReviewStore,
    item: ReviewItem,
) {
    // The name displayed in the edit fields: use nameOverride if set, else what commit() would produce.
    val displayName = item.nameOverride ?: effectiveNameForDisplay(item)
    val effectiveOrg = item.orgOverride ?: item.orgChoice ?: item.proposal.merged.org ?: ""
    // Compute effective notes: account for conflict choices and cleared conflicts, mirroring commit().
    val effectiveNotes =
        item.notesOverride ?: when {
            "notes" in item.clearedConflicts -> ""
            item.conflictChoices.containsKey("notes") -> item.conflictChoices["notes"] ?: ""
            else -> {
                val notesConflict = item.proposal.conflicts.firstOrNull { it.field == "notes" }
                notesConflict?.chosen ?: item.proposal.merged.notes ?: ""
            }
        }

    FieldGroup("Edit name components") {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            OutlinedTextField(
                value = displayName.prefix ?: "",
                onValueChange = { store.setNameComponent(item.id, NameComponent.PREFIX, it) },
                label = { Text("Prefix") },
                modifier = Modifier.weight(1f).testTag("name-prefix"),
                singleLine = true,
            )
            OutlinedTextField(
                value = displayName.given ?: "",
                onValueChange = { store.setNameComponent(item.id, NameComponent.GIVEN, it) },
                label = { Text("Given") },
                modifier = Modifier.weight(2f).testTag("name-given"),
                singleLine = true,
            )
            OutlinedTextField(
                value = displayName.middle ?: "",
                onValueChange = { store.setNameComponent(item.id, NameComponent.MIDDLE, it) },
                label = { Text("Middle") },
                modifier = Modifier.weight(1f).testTag("name-middle"),
                singleLine = true,
            )
            OutlinedTextField(
                value = displayName.family ?: "",
                onValueChange = { store.setNameComponent(item.id, NameComponent.FAMILY, it) },
                label = { Text("Family") },
                modifier = Modifier.weight(2f).testTag("name-family"),
                singleLine = true,
            )
            OutlinedTextField(
                value = displayName.suffix ?: "",
                onValueChange = { store.setNameComponent(item.id, NameComponent.SUFFIX, it) },
                label = { Text("Suffix") },
                modifier = Modifier.weight(1f).testTag("name-suffix"),
                singleLine = true,
            )
        }
    }

    FieldGroup("Edit org") {
        OutlinedTextField(
            value = effectiveOrg,
            onValueChange = { store.setOrgOverride(item.id, it) },
            label = { Text("Org") },
            modifier = Modifier.fillMaxWidth().testTag("org-edit"),
            singleLine = true,
        )
    }

    FieldGroup("Edit notes") {
        OutlinedTextField(
            value = effectiveNotes,
            onValueChange = { store.setNotesOverride(item.id, it) },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth().testTag("notes-edit"),
            minLines = 2,
        )
        Button(
            onClick = { store.appendSourceNotes(item.id) },
            modifier = Modifier.padding(top = Dimens.xs).testTag("append-notes"),
        ) {
            Text("Append source notes")
        }
    }
}

/**
 * Returns the ContactName that would be used for display / seeding (what commit() currently
 * produces before any nameOverride), for pre-filling the editable fields.
 */
private fun effectiveNameForDisplay(item: ReviewItem): ContactName =
    when {
        item.nameCleared -> ContactName()
        item.nameChoiceId != null ->
            item.proposal.cluster.members
                .firstOrNull { it.id == item.nameChoiceId }
                ?.name
                ?: item.proposal.merged.name
        else -> item.proposal.merged.name
    }

@Composable
private fun CompanyOrgField(
    store: MergeReviewStore,
    item: ReviewItem,
) {
    val members = item.proposal.cluster.members
    val orgs = members.mapNotNull { it.org?.takeIf { o -> o.isNotBlank() } }
    val promotions = members.mapNotNull { m -> displayName(m.name).takeIf { it.isNotBlank() } }
    // value -> label, insertion-ordered, de-duplicated; promotions tagged "(from name)"; "" = none.
    val candidates = LinkedHashMap<String, String>()
    orgs.forEach { candidates.putIfAbsent(it, it) }
    promotions.forEach { candidates.putIfAbsent(it, "$it (from name)") }
    candidates[""] = "(none)"

    val chosen = item.orgChoice ?: item.proposal.merged.org ?: ""
    FieldGroup("Company / org (pick one)") {
        candidates.forEach { (value, label) ->
            RadioRow(label = label, selected = value == chosen, onClick = { store.chooseOrg(item.id, value) })
        }
    }
}

@Composable
private fun SourceCards(members: List<Contact>) {
    val sorted = members.sortedWith(compareByDescending(nullsLast()) { it.modifiedAt })
    SectionHeader("Source cards (${sorted.size})")
    Row(Modifier.fillMaxWidth()) {
        sorted.forEachIndexed { index, m ->
            Box(Modifier.weight(1f)) {
                SourceCard(contact = m, primary = index == 0, selected = index == 0)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiField(
    field: String,
    values: List<String>,
    item: ReviewItem,
    store: MergeReviewStore,
) {
    if (values.isEmpty()) return
    FieldGroup("$field (keep any)") {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEach { value ->
                val ev = ExcludedValue(field, value)
                ValuePill(
                    label = value,
                    removed = ev in item.excludedValues,
                    onToggle = { store.toggleField(item.id, ev) },
                    tag = "$field:$value",
                )
            }
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
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { onCreate(selected.toList()) }, enabled = selected.size >= 2) { Text("Create merge") }
            Button(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}

private fun defaultNameMemberId(members: List<Contact>): String {
    val pool = members.filter { displayName(it.name).isNotBlank() }.ifEmpty { members }
    return pool
        .maxByOrNull { m ->
            listOf(m.name.prefix, m.name.given, m.name.middle, m.name.family, m.name.suffix).count { !it.isNullOrBlank() }
        }?.id
        ?: pool.first().id
}

package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.core.rules.TextField
import com.robsartin.contactotomy.ui.theme.Dimens
import java.time.Instant

/**
 * A modal dialog for creating or editing a single deletion rule.
 *
 * Driven entirely off [store]. [onSave] is called with the built [Rule] when the user
 * clicks Save (only enabled when [RuleBuilderStore.isValid]). [onCancel] is called when
 * the user dismisses the dialog.
 */
@Composable
fun RuleBuilderDialog(
    store: RuleBuilderStore,
    onSave: (Rule) -> Unit,
    onCancel: () -> Unit,
) {
    val state by store.state.collectAsState()
    // Derive validity and matchCount from state so recomposition fires on change.
    val valid = state.name.isNotBlank() && state.root.toConditionOrNull() != null
    val matchCount = if (valid) store.matchCount() else null

    Dialog(onDismissRequest = onCancel) {
        Surface(
            modifier = Modifier.widthIn(min = 480.dp, max = 800.dp),
            elevation = 8.dp,
        ) {
            Column(
                Modifier
                    .padding(Dimens.lg)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Rule name
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { store.setName(it) },
                    label = { Text("Rule name") },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("rule-name"),
                )

                // Live match count
                val matchText =
                    if (matchCount == null) {
                        "matches: — (incomplete rule)"
                    } else {
                        "matches $matchCount contact(s)"
                    }
                Text(
                    matchText,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = Dimens.xs).testTag("match-count"),
                )

                // Recursive condition editor
                NodeEditor(node = state.root, store = store, isRoot = true)

                // Save / Cancel buttons
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { store.toRuleOrNull()?.let { onSave(it) } },
                        enabled = valid,
                        modifier = Modifier.testTag("save-rule"),
                    ) { Text("Save") }
                    TextButton(
                        onClick = onCancel,
                        modifier =
                            Modifier
                                .padding(start = Dimens.sm)
                                .testTag("cancel-rule"),
                    ) { Text("Cancel") }
                }
            }
        }
    }
}

/**
 * Recursively renders a single [BuilderNode] and its children (for branches).
 * Each node is driven off its [id] through [store] intent methods.
 */
@Composable
private fun NodeEditor(
    node: BuilderNode,
    store: RuleBuilderStore,
    isRoot: Boolean,
    indent: Int = 0,
) {
    val leftPad = (indent * 16).dp
    Column(Modifier.padding(start = leftPad, top = Dimens.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Node-kind dropdown
            NodeKindDropdown(node = node, store = store)

            // Delete button (hidden for root)
            if (!isRoot) {
                TextButton(
                    onClick = { store.removeNode(node.id) },
                    modifier = Modifier.testTag("delete:${node.id}"),
                ) { Text("✕") }
            }
        }

        // Kind-specific inputs
        when (node) {
            is LeafText -> LeafTextInputs(node, store)
            is LeafPhone -> LeafPhoneInputs(node, store)
            is LeafPredicate -> LeafPredicateInputs(node, store)
            is BranchAnd -> {
                node.children.forEach { child ->
                    NodeEditor(node = child, store = store, isRoot = false, indent = indent + 1)
                }
                TextButton(
                    onClick = { store.addChild(node.id) },
                    modifier = Modifier.padding(start = ((indent + 1) * 16).dp).testTag("add-child:${node.id}"),
                ) { Text("+ Add condition") }
            }
            is BranchOr -> {
                node.children.forEach { child ->
                    NodeEditor(node = child, store = store, isRoot = false, indent = indent + 1)
                }
                TextButton(
                    onClick = { store.addChild(node.id) },
                    modifier = Modifier.padding(start = ((indent + 1) * 16).dp).testTag("add-child:${node.id}"),
                ) { Text("+ Add condition") }
            }
            is BranchNot -> {
                if (node.child != null) {
                    NodeEditor(node = node.child, store = store, isRoot = false, indent = indent + 1)
                } else {
                    TextButton(
                        onClick = { store.addChild(node.id) },
                        modifier = Modifier.padding(start = ((indent + 1) * 16).dp).testTag("add-child:${node.id}"),
                    ) { Text("+ Add condition") }
                }
            }
        }
    }
}

/** Dropdown button that shows the current node kind and allows switching. */
@Composable
private fun NodeKindDropdown(
    node: BuilderNode,
    store: RuleBuilderStore,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentKind =
        when (node) {
            is LeafText -> NodeKind.TEXT
            is LeafPhone -> NodeKind.PHONE
            is LeafPredicate -> NodeKind.PREDICATE
            is BranchAnd -> NodeKind.AND
            is BranchOr -> NodeKind.OR
            is BranchNot -> NodeKind.NOT
        }

    Button(
        onClick = { expanded = true },
        modifier = Modifier.testTag("node-kind:${node.id}"),
    ) {
        Text(currentKind.label())
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        NodeKind.entries.forEach { kind ->
            DropdownMenuItem(
                onClick = {
                    store.changeKind(node.id, kind)
                    expanded = false
                },
            ) {
                Text(kind.label())
            }
        }
    }
}

@Composable
private fun LeafTextInputs(
    node: LeafText,
    store: RuleBuilderStore,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Field dropdown
        TextFieldDropdown(node = node, store = store)
        OutlinedTextField(
            value = node.glob,
            onValueChange = { store.setGlob(node.id, it) },
            label = { Text("glob pattern") },
            modifier =
                Modifier
                    .weight(1f)
                    .testTag("glob:${node.id}"),
        )
    }
    if (node.glob.isBlank()) {
        Text("Pattern required", fontSize = 11.sp, modifier = Modifier.padding(start = Dimens.sm))
    }
}

@Composable
private fun TextFieldDropdown(
    node: LeafText,
    store: RuleBuilderStore,
) {
    var expanded by remember { mutableStateOf(false) }
    Button(
        onClick = { expanded = true },
        modifier = Modifier.testTag("field:${node.id}"),
    ) {
        Text(node.field.name)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        TextField.entries.forEach { f ->
            DropdownMenuItem(
                onClick = {
                    store.setField(node.id, f)
                    expanded = false
                },
            ) {
                Text(f.name)
            }
        }
    }
}

@Composable
private fun LeafPhoneInputs(
    node: LeafPhone,
    store: RuleBuilderStore,
) {
    OutlinedTextField(
        value = node.pattern,
        onValueChange = { store.setPattern(node.id, it) },
        label = { Text("phone pattern") },
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("pattern:${node.id}"),
    )
    if (node.pattern.isBlank()) {
        Text("Pattern required", fontSize = 11.sp, modifier = Modifier.padding(start = Dimens.sm))
    }
}

@Composable
private fun LeafPredicateInputs(
    node: LeafPredicate,
    store: RuleBuilderStore,
) {
    // Predicate kind dropdown
    PredicateKindDropdown(node = node, store = store)

    when (node.kind) {
        PredicateKind.CREATED_BEFORE -> {
            OutlinedTextField(
                value = node.before?.toString() ?: "",
                onValueChange = { text ->
                    store.setBefore(node.id, runCatching { Instant.parse(text) }.getOrNull())
                },
                label = { Text("ISO-8601 date (e.g. 2023-01-01T00:00:00Z)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (node.before == null) {
                Text("Date required", fontSize = 11.sp, modifier = Modifier.padding(start = Dimens.sm))
            }
        }
        PredicateKind.SOURCE_IS -> {
            SourceDropdown(node = node, store = store)
            if (node.source == null) {
                Text("Source required", fontSize = 11.sp, modifier = Modifier.padding(start = Dimens.sm))
            }
        }
        else -> { /* no additional inputs */ }
    }
}

@Composable
private fun PredicateKindDropdown(
    node: LeafPredicate,
    store: RuleBuilderStore,
) {
    var expanded by remember { mutableStateOf(false) }
    Button(
        onClick = { expanded = true },
        modifier = Modifier.testTag("predicate-kind:${node.id}"),
    ) {
        Text(node.kind.label())
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        PredicateKind.entries.forEach { kind ->
            DropdownMenuItem(
                onClick = {
                    store.setPredicateKind(node.id, kind)
                    expanded = false
                },
            ) {
                Text(kind.label())
            }
        }
    }
}

@Composable
private fun SourceDropdown(
    node: LeafPredicate,
    store: RuleBuilderStore,
) {
    var expanded by remember { mutableStateOf(false) }
    Button(
        onClick = { expanded = true },
        modifier = Modifier.testTag("source:${node.id}"),
    ) {
        Text(node.source?.name ?: "Select source…")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        Source.entries.forEach { src ->
            DropdownMenuItem(
                onClick = {
                    store.setSource(node.id, src)
                    expanded = false
                },
            ) {
                Text(src.name)
            }
        }
    }
}

// ─── Label helpers ────────────────────────────────────────────────────────────

private fun NodeKind.label(): String =
    when (this) {
        NodeKind.TEXT -> "Text match"
        NodeKind.PHONE -> "Phone match"
        NodeKind.PREDICATE -> "Predicate"
        NodeKind.AND -> "AND (all of)"
        NodeKind.OR -> "OR (any of)"
        NodeKind.NOT -> "NOT"
    }

private fun PredicateKind.label(): String =
    when (this) {
        PredicateKind.NO_NAME_AND_NO_PHONE -> "No name & no phone"
        PredicateKind.NO_PHONE -> "No phone"
        PredicateKind.NO_EMAIL -> "No email"
        PredicateKind.EMPTY_CARD -> "Empty card"
        PredicateKind.CREATED_BEFORE -> "Created before…"
        PredicateKind.SOURCE_IS -> "Source is…"
    }

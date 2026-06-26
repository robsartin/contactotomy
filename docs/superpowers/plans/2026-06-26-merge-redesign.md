# Merge Screen Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the merge review so every cluster starts undecided, deciding moves it from "Needs review" to a "Resolved" section (with Undo), and the detail pane clearly identifies each source card and uses real Checkbox/RadioButton controls for field/value selection.

**Architecture:** State-model + screen change only. `MergeReviewTypes`/`MergeReviewStore` get `Decision.PENDING` (default), accept/reject/undo, a name choice, and a name override applied in `commit()` after the unchanged `core` engine's `applyDecisions`. `MergeScreen` is redesigned. The `core` matcher/merger/apply engine is untouched. See `docs/superpowers/specs/2026-06-26-merge-redesign-design.md`. Tracking issue #26.

**Tech Stack:** Kotlin 2.0.21, Compose Desktop (Material `Checkbox`/`RadioButton`), JUnit5, Kover (≥90 line / ≥70 branch), `./gradlew`.

---

## Conventions for every task

- Build/test with `./gradlew` (NEVER system `gradle`); Compose UI tests run headlessly.
- Strict TDD / refactor-with-tests: for behavior changes, update the test to the new expectation first (see it fail), then change the code (see it pass).
- Before each commit: `./gradlew spotlessApply && ./gradlew check` → BUILD SUCCESSFUL (tests + Kover 90/70 + spotlessCheck + Konsist).
- UI code only in `ui`; no `androidx.compose` in `core`. The `core` engine must NOT change.
- Tests use the shared `com.robsartin.contactotomy.testsupport.contact` factory; `assertExists()`/`assertIsNotEnabled()` are Compose-test member calls — match the existing UI tests' import style.
- Branch `26-merge-redesign` (already checked out); reference #26 in commit messages.

---

### Task 1: State model — PENDING default, accept/reject/undo, name choice, name override

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Modify (compile-fix only; full redesign in Task 2): `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
- Update tests: `MergeReviewStoreBuildTest`, `MergeReviewStoreIntentsTest`, `MergeReviewStoreCommitTest`, `MergeReviewStoreOverlapTest`, `AppNavigationTest`

- [ ] **Step 1: Update the types**

In `MergeReviewTypes.kt`, replace the `Decision` enum and add `nameChoiceId` with a PENDING default:
```kotlin
enum class Decision { PENDING, ACCEPT, REJECT }

data class ReviewItem(
    val id: String,
    val origin: Origin,
    val proposal: MergeProposal,
    val decision: Decision = Decision.PENDING,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
    val nameChoiceId: String? = null,
)
```

- [ ] **Step 2: Update the store — defaults, intents, name override**

In `MergeReviewStore.kt`:

`buildItems()` — both high and uncertain start `PENDING` (drop the explicit decisions):
```kotlin
    private fun buildItems(): List<ReviewItem> {
        val result = matcher.match(contacts)
        val high = result.clusters.map { cluster ->
            ReviewItem(id = cluster.id, origin = Origin.HIGH, proposal = merger.merge(cluster))
        }
        val uncertain = result.uncertainPairs.map { edge ->
            val cluster = Cluster(
                id = "uncertain-${edge.a.id}+${edge.b.id}",
                members = listOf(edge.a, edge.b),
                confidence = Confidence.UNCERTAIN,
                reasons = edge.reasons,
            )
            ReviewItem(id = cluster.id, origin = Origin.UNCERTAIN, proposal = merger.merge(cluster))
        }
        return high + uncertain
    }
```

Replace `setDecision` with `accept`/`reject`/`undo`, and add `chooseName`:
```kotlin
    fun accept(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.ACCEPT) }

    fun reject(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.REJECT) }

    fun undo(itemId: String) = updateItem(itemId) { it.copy(decision = Decision.PENDING) }

    fun chooseName(itemId: String, memberId: String) = updateItem(itemId) { it.copy(nameChoiceId = memberId) }
```
(Keep `toggleField`, `chooseConflict`, `acceptAllHighConfidence`, `updateItem` as they are.)

In `commit()`: change the downgrade target from `Decision.SKIP` to `Decision.PENDING`, and apply the name override to the result before returning. Replace the tail of `commit()`:
```kotlin
        val result = DecisionApplier().applyDecisions(
            contacts, finalAccepted.map { it.proposal }, decisions,
        )
        // Apply per-cluster name overrides (chosen source card's name) — kept in the UI, engine untouched.
        val nameOverrides: Map<String, com.robsartin.contactotomy.core.model.ContactName> =
            finalAccepted.mapNotNull { item ->
                val memberId = item.nameChoiceId ?: return@mapNotNull null
                val member = item.proposal.cluster.members.firstOrNull { it.id == memberId } ?: return@mapNotNull null
                item.proposal.merged.id to member.name
            }.toMap()
        val withNames = result.map { c -> nameOverrides[c.id]?.let { c.copy(name = it) } ?: c }

        _state.update { st ->
            st.copy(
                items = st.items.map { if (it.id in downgraded) it.copy(decision = Decision.PENDING) else it },
                committed = true,
            )
        }
        return withNames
```

- [ ] **Step 3: Compile-fix `MergeScreen.kt` to the new API**

`MergeScreen` currently references `Decision.SKIP` and `setDecision`. Make the minimal change so it compiles (the full redesign is Task 2): in `ClusterRow`, change the `mark` `when` to `PENDING -> "•"`, `ACCEPT -> "✓"`, `REJECT -> "✕"`; in `MergeDetail`, replace the three decision buttons with two calling the new intents:
```kotlin
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { store.accept(item.id) }) { Text("Accept") }
            Button(onClick = { store.reject(item.id) }) { Text("Keep separate") }
        }
```
Leave the rest of `MergeScreen` as-is for now.

- [ ] **Step 4: Update the store/nav tests to the new model (write expected behavior, then it passes against Steps 1–3)**

- `MergeReviewStoreBuildTest`: change the HIGH item assertion from `Decision.ACCEPT` to `Decision.PENDING`, and the UNCERTAIN item from `Decision.REJECT` to `Decision.PENDING`.
- `MergeReviewStoreIntentsTest`: replace `setDecision(id, Decision.REJECT)` with `reject(id)`; the `acceptAllHighConfidence` test should `reject(id)` then `acceptAllHighConfidence()` and assert `ACCEPT`. Add an `undo` assertion: `accept(id)`, then `undo(id)`, assert `PENDING`.
- `MergeReviewStoreCommitTest`: the "commit merges accepted clusters" test must `accept(id)` the cluster before `commit()` (default is now PENDING). Keep the "rejected not merged" test. Add a name-override test:
  ```kotlin
  @Test
  fun `commit applies the chosen source name to the merged contact`() {
      val a = contact("a", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
      val b = contact("b", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
      val store = MergeReviewStore(listOf(a, b))
      val item = store.state.value.items.single()
      store.accept(item.id)
      store.chooseName(item.id, "b") // choose the "Rob" card's name
      val merged = store.commit().single()
      assertEquals("Rob", merged.name.given)
  }
  ```
- `MergeReviewStoreOverlapTest`: where it asserts a downgraded item became `Decision.SKIP`, change the expectation to `Decision.PENDING`. Accept the items explicitly first (default PENDING).
- `AppNavigationTest`: the "Next on Merge commits (2 dups → 1)" test now needs the cluster accepted before Next. Update it to select the cluster and accept it in the UI before clicking Next:
  ```kotlin
  store.goTo(Screen.MERGE)
  setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
  onAllNodesWithText("Sartin", substring = true).onFirst().performClick() // select the cluster
  onNodeWithText("Accept", substring = true).performClick()
  onNodeWithText("Next").performClick()
  assertEquals(1, store.state.value.mergedContacts?.size)
  assertEquals(Screen.DELETION, store.state.value.screen)
  ```
  (The other AppNavigationTest cases — Deletion/Export/Import — are unaffected.)

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreBuildTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreIntentsTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCommitTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreOverlapTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppNavigationTest.kt
git commit -m "feat(ui): merge review starts undecided; accept/reject/undo + name choice (#26)"
```

---

### Task 2: Redesign the MergeScreen (sections, source cards, real controls)

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt` (full rewrite of the composables)
- Update tests: `MergeScreenListTest`, `MergeDetailTest`, `MergeBeforeCardsTest`, `MergeUncertainReasonTest`

- [ ] **Step 1: Replace `MergeScreen.kt` with the redesigned screen**

```kotlin
package com.robsartin.contactotomy.ui

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
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    Row(Modifier.fillMaxWidth().fillMaxHeight().padding(top = 8.dp)) {
        // ---- LEFT: review list ----
        Column(Modifier.fillMaxWidth(0.42f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Needs review (${pending.size})")
                if (pending.any { it.origin == Origin.HIGH }) {
                    Button(onClick = { store.acceptAllHighConfidence() }) { Text("Accept all high-confidence") }
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
        Column(Modifier.fillMaxWidth().padding(start = 12.dp).verticalScroll(rememberScrollState())) {
            if (selected == null) {
                Text("All clusters reviewed")
            } else {
                MergeDetail(store, selected) {
                    selectedId = pending.firstOrNull { it.id != selected.id }?.id
                }
            }
        }
    }
}

@Composable
private fun ClusterRow(item: ReviewItem, selected: Boolean, onClick: () -> Unit) {
    val tag = if (item.origin == Origin.UNCERTAIN) "maybe" else "HIGH"
    val label = if (item.origin == Origin.UNCERTAIN) {
        val names = item.proposal.cluster.members.joinToString(" ↔ ") { displayName(it.name) }
        "$names · ${item.proposal.cluster.reasons.joinToString(", ")}"
    } else {
        "${displayName(item.proposal.merged.name)} · ${item.proposal.cluster.members.size} cards"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Button(onClick = onClick) { Text((if (selected) "▸ " else "") + "$label  [$tag]") }
    }
}

@Composable
private fun ResolvedRow(item: ReviewItem, onUndo: () -> Unit) {
    val mark = if (item.decision == Decision.ACCEPT) "✓ will merge" else "✕ kept separate"
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$mark · ${displayName(item.proposal.merged.name)}")
        Button(onClick = onUndo) { Text("Undo") }
    }
}

@Composable
private fun MergeDetail(store: MergeReviewStore, item: ReviewItem, onDecided: () -> Unit) {
    val p = item.proposal
    Column {
        SourceCards(p.cluster.members)

        Text("Merged result — tick what to keep", Modifier.padding(top = 8.dp, bottom = 4.dp))

        // Name: pick which source card's name wins (most-complete pre-selected = merged.name).
        val names = p.cluster.members.associate { it.id to displayName(it.name) }.filterValues { it.isNotBlank() }
        if (names.isNotEmpty()) {
            Text("Name (pick one)")
            names.forEach { (memberId, name) ->
                val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = value == chosen, onClick = { store.chooseConflict(item.id, conflict.field, value) })
                    Text(value)
                }
            }
        }

        Row(Modifier.padding(top = 10.dp)) {
            Button(onClick = { store.accept(item.id); onDecided() }) { Text("✓ Accept merge") }
            Button(onClick = { store.reject(item.id); onDecided() }) { Text("✕ Keep separate") }
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
private fun MultiField(field: String, values: List<String>, item: ReviewItem, store: MergeReviewStore) {
    if (values.isEmpty()) return
    Text("$field (keep any)", Modifier.padding(top = 4.dp))
    values.forEach { value ->
        val ev = ExcludedValue(field, value)
        val included = ev !in item.excludedValues
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = included, onCheckedChange = { store.toggleField(item.id, ev) })
            Text(value)
        }
    }
}

private fun defaultNameMemberId(members: List<Contact>): String =
    members.maxByOrNull { listOf(it.name.prefix, it.name.given, it.name.middle, it.name.family, it.name.suffix).count { p -> !p.isNullOrBlank() } }?.id
        ?: members.first().id

private fun displayName(name: ContactName): String =
    name.formatted ?: listOfNotNull(name.given, name.family).joinToString(" ")
```

Note: this is a complete reference implementation. If a Compose layout modifier needs adjusting to compile/render (e.g. nested-scroll height bounds), fix it minimally while preserving the structure and behavior; verify with the UI tests and the run-the-app step. Do not change `core`.

- [ ] **Step 2: Update the MergeScreen UI tests to the new structure**

- `MergeScreenListTest`: `MergeScreen` no longer takes `onCommit` (already the case after #24). Update the "shows the to-merge section" assertion to the new header text: `onNodeWithText("Needs review", substring = true).assertExists()` and that a cluster's name renders. Add a test that **deciding moves a cluster to Resolved**: build a store with one HIGH duplicate cluster, render `MergeScreen(store)`, click "Accept merge" (the cluster auto-selects), then assert `onNodeWithText("Resolved", substring = true).assertExists()` and the store item's decision is `ACCEPT`.
- `MergeDetailTest`: replace the chip-click assertions with the real controls — selecting a cluster shows "Source cards", a phone value renders with a `Checkbox`; toggling it sets `excludedValues` (assert store state). Keep using `onAllNodesWithText(...).onFirst()` where text is duplicated across a source card and the merged list.
- `MergeBeforeCardsTest`: rename expectations to "Source cards"; assert a source/`[APPLE]`/`[GOOGLE]` label renders for a member.
- `MergeUncertainReasonTest`: still asserts an uncertain row shows its reason; update any control/label text that changed.

(Adjust Compose test-API imports to the installed version; use `onAllNodesWithText(...).onFirst()` for any text that now appears in both a source card and the merged result.)

- [ ] **Step 3: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenListTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeDetailTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeBeforeCardsTest.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeUncertainReasonTest.kt
git commit -m "feat(ui): redesign merge screen — sections, source cards, checkbox/radio (#26)"
```

---

### Task 3: Run-the-app verification

- [ ] **Step 1: Full gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL — coverage ≥90/70; all engine + UI tests green. If branch coverage dips below 70, add a focused UI/store test (e.g. Undo returns a cluster to Needs review; a RadioButton name choice) rather than lowering the floor.

- [ ] **Step 2: Run the app (controller-verified)**

Run: `./gradlew run`
Expected: import a `.vcf` with some duplicates, go to Merge: clusters appear under "Needs review" (none pre-decided); selecting one shows distinct source cards (source badge, ★primary, date) and the merged result with checkboxes (phones/emails/labels) and radios (name, org); clicking "Accept merge" / "Keep separate" moves the cluster to "Resolved" (with Undo) and advances to the next; the "N still pending" count updates; "Accept all high-confidence" clears the HIGH ones; then the wizard Next commits. Screenshot/notes for the PR. (The controller runs this; it needs a display.)

---

## Self-Review

**Spec coverage:**
- §2/§3 PENDING default, drop SKIP, accept/reject/undo, acceptAllHighConfidence, commit ACCEPT-only, name override in store, downgrade→PENDING, nameChoiceId: Task 1. ✓
- §4 list (Needs review/Resolved + progress + Undo + Accept-all), detail (source cards w/ badge/primary/date; Checkbox multi-value; RadioButton name + conflict; decide→advance), "N still pending": Task 2. ✓
- §5 testing (store + Compose UI); §6 scope (engine unchanged; name override in store): Tasks 1–2; run-the-app Task 3. ✓

**Placeholder scan:** No TBD/TODO; the new `MergeScreen` is a complete reference implementation (the layout-tuning note is an allowance for Compose modifier specifics, not missing logic).

**Type consistency:** `Decision { PENDING, ACCEPT, REJECT }`, `ReviewItem(... decision = PENDING, nameChoiceId)`, store `accept/reject/undo/chooseName/toggleField/chooseConflict/acceptAllHighConfidence/commit`, `MergeScreen(store)` (no onCommit) are consistent across Tasks 1–2 and the updated tests. `commit()` name override matches `proposal.merged.id` (the id `applyDecisions` preserves on the merged contact). `App.kt` already calls `MergeScreen(mergeStore)` (post-#24) — unchanged.

**Risk notes:** (1) Default change to PENDING breaks several existing assertions — Task 1 Step 4 updates them all in the same commit to stay green; the `AppNavigationTest` merge case now accepts via the UI before Next. (2) Nested scroll/`LazyColumn` height bounds in `MergeScreen` may need a modifier tweak to render — fix minimally, verify with run-the-app. (3) Coverage at the 70% branch floor is tight — add a focused test if `koverVerify` dips.

# Manual Merge (4b-2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user force-merge two or more contacts the matcher never clustered, inside the redesigned merge screen, reusing the existing review/commit flow.

**Architecture:** A manual merge is just another `ReviewItem` with `Origin.MANUAL`. The store builds a core `Cluster` from the chosen contacts, runs the existing `ContactMerger.merge`, and appends a `PENDING` item — which then uses the same accept / keep-separate / commit / name / checkbox / radio machinery. No `core` changes. The screen adds a "+ Manual merge" button opening an in-window searchable picker.

**Tech Stack:** Kotlin, Compose Desktop (Material), `runComposeUiTest`, kotlinx-coroutines StateFlow, JUnit/kotlin-test, Kover (line ≥90 / branch ≥70), Spotless/ktlint, Konsist.

Branch: `32-manual-merge` (off `main` incl. #31). Issue: #32.
Spec: `docs/superpowers/specs/2026-06-26-manual-merge-design.md`.

## File Structure

- Modify `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt` — add `eligibleForManualMerge()` and `manualMerge(...)`.
- Modify `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt` — "manual" tag in `ClusterRow`; "+ Manual merge" button; `ManualMergePicker` composable.
- Create `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt` — store unit tests.
- Create `src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenManualMergeTest.kt` — picker UI tests.
- Modify `src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt` — one e2e case.
- Create `src/test/resources/fixtures/manual-merge.vcf` — two un-clustered cards.

`MergeReviewTypes.kt` needs no change (`Origin.MANUAL` and all `ReviewItem` fields already exist).

---

### Task 1: Store — `eligibleForManualMerge()`

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt` (create)
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`

- [ ] **Step 1: Write the failing test**

Create `MergeReviewStoreManualMergeTest.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeReviewStoreManualMergeTest {
    // a & b cluster (shared phone + nickname); c & d are lone singletons.
    private fun store(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        return MergeReviewStore(listOf(a, b, c, d))
    }

    @Test
    fun `eligible pool excludes cards already in a cluster`() {
        val ids = store().eligibleForManualMerge().map { it.id }.toSet()
        assertEquals(setOf("c", "d"), ids)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreManualMergeTest"`
Expected: FAIL — unresolved reference `eligibleForManualMerge`.

- [ ] **Step 3: Write minimal implementation**

In `MergeReviewStore.kt`, add this method (e.g. just after `acceptAllHighConfidence()`):

```kotlin
    /** Contacts not already a member of any current review item — the manual-merge pool. */
    fun eligibleForManualMerge(): List<Contact> {
        val claimed =
            _state.value.items
                .flatMap { it.proposal.cluster.members }
                .map { it.id }
                .toSet()
        return contacts.filter { it.id !in claimed }
    }
```

(`Contact` is already imported in this file.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreManualMergeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt
git commit -m "feat(ui): eligibleForManualMerge pool excludes clustered cards (#32)"
```

---

### Task 2: Store — `manualMerge(memberIds)`

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt` (modify)
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`

- [ ] **Step 1: Write the failing tests**

Add to `MergeReviewStoreManualMergeTest.kt`:

```kotlin
    @Test
    fun `manualMerge appends a PENDING MANUAL item over the chosen members`() {
        val store = store()
        val id = store.manualMerge(listOf("c", "d"))
        val item = store.state.value.items.first { it.id == id }
        assertEquals(Origin.MANUAL, item.origin)
        assertEquals(Decision.PENDING, item.decision)
        assertEquals(
            setOf("c", "d"),
            item.proposal.cluster.members
                .map { it.id }
                .toSet(),
        )
    }

    @Test
    fun `manualMerge removes its members from the eligible pool`() {
        val store = store()
        store.manualMerge(listOf("c", "d"))
        assertEquals(emptyList(), store.eligibleForManualMerge())
    }

    @Test
    fun `manualMerge with fewer than two eligible ids is a no-op returning null`() {
        val store = store()
        val before = store.state.value.items.size
        assertEquals(null, store.manualMerge(listOf("c")))
        // ids not in the eligible pool are ignored (a & b are already clustered)
        assertEquals(null, store.manualMerge(listOf("c", "a")))
        assertEquals(before, store.state.value.items.size)
    }
```

Add the import at the top if missing: `import kotlin.test.assertEquals` is already there; no new imports needed.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreManualMergeTest"`
Expected: FAIL — unresolved reference `manualMerge`.

- [ ] **Step 3: Write minimal implementation**

In `MergeReviewStore.kt`, add this method right after `eligibleForManualMerge()`:

```kotlin
    /**
     * Force-merges [memberIds] (>= 2 eligible contacts) into a new PENDING MANUAL item.
     * Returns the new item id, or null if fewer than two eligible contacts were given.
     */
    fun manualMerge(memberIds: List<String>): String? {
        val eligible = eligibleForManualMerge().associateBy { it.id }
        val members = memberIds.distinct().mapNotNull { eligible[it] }
        if (members.size < 2) return null
        val cluster =
            Cluster(
                id = "manual-" + members.map { it.id }.sorted().joinToString("+"),
                members = members,
                confidence = Confidence.HIGH,
                reasons = emptyList(),
            )
        val item = ReviewItem(id = cluster.id, origin = Origin.MANUAL, proposal = merger.merge(cluster))
        _state.update { st -> st.copy(items = st.items + item) }
        return item.id
    }
```

(`Cluster`, `Confidence`, and `merger` are already imported/available in this file.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreManualMergeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt
git commit -m "feat(ui): manualMerge appends a PENDING MANUAL review item (#32)"
```

---

### Task 3: Store — a manual item commits like any accepted merge

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt` (modify)

This proves a `MANUAL` item flows through the existing `commit()` unchanged.

- [ ] **Step 1: Write the failing test**

Add to `MergeReviewStoreManualMergeTest.kt`:

```kotlin
    @Test
    fun `accepting then committing a manual item collapses its cards`() {
        val store = store()
        val id = store.manualMerge(listOf("c", "d"))!!
        store.accept(id)
        val result = store.commit()
        // a & b untouched (2), c & d collapse into 1 merged contact => 3 total.
        assertEquals(3, result.size)
        // the two source ids are gone; the merged id is present.
        val ids = result.map { it.id }.toSet()
        assertEquals(false, ids.contains("c"))
        assertEquals(false, ids.contains("d"))
    }
```

- [ ] **Step 2: Run test to verify it fails... or passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreManualMergeTest"`
Expected: PASS immediately (commit already merges every ACCEPT item regardless of origin). This is a **characterization test** locking in that behavior — if it does not pass, stop and investigate before changing `commit()`.

- [ ] **Step 3: (no implementation needed)**

`commit()` is unchanged. If Step 2 passed, proceed.

- [ ] **Step 4: Commit**

```bash
git add src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreManualMergeTest.kt
git commit -m "test(ui): manual item commits via the existing accept flow (#32)"
```

---

### Task 4: Screen — `ClusterRow` shows a "manual" tag for `Origin.MANUAL`

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenManualMergeTest.kt` (create)
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`

- [ ] **Step 1: Write the failing test**

Create `MergeScreenManualMergeTest.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeScreenManualMergeTest {
    // c & d are lone singletons; no auto clusters.
    private fun store(): MergeReviewStore {
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        return MergeReviewStore(listOf(c, d))
    }

    @Test
    fun `a created manual item is tagged manual in the list`() =
        runComposeUiTest {
            val store = store()
            store.manualMerge(listOf("c", "d"))
            setContent { MergeScreen(store) }
            onNodeWithText("[manual]", substring = true).assertIsDisplayed()
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `xvfb-run -a ./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenManualMergeTest"`
(Locally on macOS, omit `xvfb-run -a`. CI runs under xvfb.)
Expected: FAIL — the row currently renders `[HIGH]` for non-uncertain items, so `[manual]` is not found.

- [ ] **Step 3: Write minimal implementation**

In `MergeScreen.kt`, inside `ClusterRow`, replace:

```kotlin
    val tag = if (item.origin == Origin.UNCERTAIN) "maybe" else "HIGH"
```

with:

```kotlin
    val tag =
        when (item.origin) {
            Origin.UNCERTAIN -> "maybe"
            Origin.MANUAL -> "manual"
            Origin.HIGH -> "HIGH"
        }
```

(The existing label `else` branch already renders "name · N cards" for manual items — no other change.)

- [ ] **Step 4: Run test to verify it passes**

Run: `xvfb-run -a ./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenManualMergeTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenManualMergeTest.kt
git commit -m "feat(ui): tag MANUAL review items as 'manual' in the list (#32)"
```

---

### Task 5: Screen — "+ Manual merge" button and searchable picker

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenManualMergeTest.kt` (modify)
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`

- [ ] **Step 1: Write the failing tests**

Add to `MergeScreenManualMergeTest.kt`:

```kotlin
    @Test
    fun `manual merge button opens a picker that filters and creates a merge`() =
        runComposeUiTest {
            val store = store()
            setContent { MergeScreen(store) }

            onNodeWithText("+ Manual merge").performClick() // open picker
            onNodeWithText("Search").assertIsDisplayed()    // picker is shown

            // both eligible cards are listed; pick both, then create
            onNodeWithText("Morgan Quill", substring = true).performClick()
            onNodeWithText("Devon Vasquez", substring = true).performClick()
            onNodeWithText("Create merge").performClick()

            // back on the list with one PENDING manual item, auto-selected and tagged
            onNodeWithText("[manual]", substring = true).assertIsDisplayed()
            assertEquals(1, store.state.value.items.size)
            assertEquals(Origin.MANUAL, store.state.value.items.single().origin)
        }

    @Test
    fun `picker search narrows the eligible list`() =
        runComposeUiTest {
            val store = store()
            setContent { MergeScreen(store) }
            onNodeWithText("+ Manual merge").performClick()
            onNodeWithText("Search").performTextInput("Morgan")
            onNodeWithText("Morgan Quill", substring = true).assertIsDisplayed()
            onAllNodesWithText("Devon Vasquez", substring = true).assertCountEquals(0)
        }
```

Add these imports to the test file:

```kotlin
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlin.test.assertEquals
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `xvfb-run -a ./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenManualMergeTest"`
Expected: FAIL — no "+ Manual merge" node.

- [ ] **Step 3: Write the implementation**

In `MergeScreen.kt`:

(a) Add imports:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material.OutlinedTextField
import androidx.compose.runtime.mutableStateListOf
```

(b) In `MergeScreen`, add picker state next to `selectedId`:

```kotlin
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
```

(c) Immediately before the top-level `Row(Modifier.fillMaxWidth()...)` that lays out the screen, add the picker branch:

```kotlin
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
```

(d) Replace the left-panel header `Row` (the one with "Needs review (...)" + the conditional Accept-all button) with:

```kotlin
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Needs review (${pending.size})")
                Row {
                    Button(onClick = { showPicker = true }) { Text("+ Manual merge") }
                    if (pending.any { it.origin == Origin.HIGH }) {
                        Button(onClick = { store.acceptAllHighConfidence() }) { Text("Accept all high-confidence") }
                    }
                }
            }
```

(e) Add the `ManualMergePicker` composable at the end of the file (after `MultiField`, before the private helper funcs is fine):

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `xvfb-run -a ./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenManualMergeTest"`
Expected: PASS (all four tests in the class).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenManualMergeTest.kt
git commit -m "feat(ui): + Manual merge button with searchable card picker (#32)"
```

---

### Task 6: End-to-end — manual merge through the full wizard

**Files:**
- Create: `src/test/resources/fixtures/manual-merge.vcf`
- Modify: `src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt`

- [ ] **Step 1: Create the fixture**

Create `src/test/resources/fixtures/manual-merge.vcf` (two distinct people the matcher leaves as singletons — different surnames, no shared phone/email):

```
BEGIN:VCARD
VERSION:3.0
N:Quill;Morgan;;;
FN:Morgan Quill
TEL;TYPE=CELL:+1 305-555-2020
EMAIL;TYPE=INTERNET:morgan@quill.example
END:VCARD
BEGIN:VCARD
VERSION:3.0
N:Vasquez;Devon;;;
FN:Devon Vasquez
TEL;TYPE=CELL:+1 415-555-3030
EMAIL;TYPE=INTERNET:devon@vasquez.example
END:VCARD
```

- [ ] **Step 2: Write the failing test**

Add to `AppFlowTest.kt`:

```kotlin
    @Test
    fun `manual merge combines two un-clustered cards across the whole flow`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("manual-merge.vcf"), Source.APPLE) }
            assertEquals(2, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge (zero auto clusters)
            onNodeWithText("+ Manual merge").performClick()
            onNodeWithText("Morgan Quill", substring = true).performClick()
            onNodeWithText("Devon Vasquez", substring = true).performClick()
            onNodeWithText("Create merge").performClick()
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            assertEquals(
                1,
                store.state.value.finalContacts
                    ?.size,
            )
        }
```

(No new imports — `runBlocking`, `assertEquals`, `assertIsDisplayed`, `onNodeWithText`, `performClick` are all already imported in `AppFlowTest.kt`.)

- [ ] **Step 3: Run test to verify it fails (then passes once Task 5 is in)**

Run: `xvfb-run -a ./gradlew test --tests "com.robsartin.contactotomy.ui.AppFlowTest"`
Expected: PASS (Tasks 1–5 supply the behavior). If it FAILS at "+ Manual merge" or "Create merge", Task 5 is incomplete — fix there, not here.

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/fixtures/manual-merge.vcf \
        src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt
git commit -m "test(ui): e2e manual merge of two un-clustered cards (#32)"
```

---

### Task 7: Full gate, push, open PR

**Files:** none (verification + PR).

- [ ] **Step 1: Run the full gate**

Run: `xvfb-run -a ./gradlew check` (locally on macOS: `./gradlew check`)
Expected: BUILD SUCCESSFUL — all tests pass, Spotless/ktlint clean, Konsist clean, Kover line ≥90 / branch ≥70.
If Spotless fails on formatting: run `./gradlew spotlessApply`, re-run `check`, and amend the relevant commit.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin 32-manual-merge
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create --base main --head 32-manual-merge \
  --title "Manual merge: force-merge contacts the matcher missed (Closes #32)" \
  --body "Implements 4b-2 per docs/superpowers/specs/2026-06-26-manual-merge-design.md. A manual merge is a MANUAL ReviewItem flowing through the existing review/commit machinery; store gains eligibleForManualMerge + manualMerge; screen gains a + Manual merge button and a searchable picker. No core changes. Closes #32."
```

- [ ] **Step 4: Watch CI**

Run: `gh pr checks <PR#> --watch`
Expected: `build` passes. Then hand off to the user for the Claude App review + human merge.

---

## Self-Review

**Spec coverage:**
- §2 no-core / MANUAL ReviewItem → Tasks 2, 3. ✓
- §2 searchable add-list → Task 5. ✓
- §2 eligible = un-clustered only → Task 1. ✓
- §2 same-as-auto lifecycle (Accept/Keep separate/Undo) → unchanged footer + Task 4 tag; commit unchanged (Task 3). ✓
- §2 acceptAllHighConfidence ignores manual → unchanged (keyed on `Origin.HIGH`); covered implicitly (manual item is `MANUAL`). ✓
- §4 `eligibleForManualMerge` + `manualMerge` (`String?`, ≥2, `reasons = emptyList()`) → Tasks 1, 2. ✓
- §5 button + in-window picker (search, checkbox, Create at ≥2, Cancel) → Task 5. ✓
- §5 picker state transient `remember` → Task 5 (no store state added). ✓
- §5 manual tag → Task 4. ✓
- §6 store tests, UI tests, e2e, floors/Konsist → Tasks 1–3, 4–5, 6, 7. ✓

**Placeholder scan:** none — every code/test step has full code and exact commands.

**Type consistency:** `eligibleForManualMerge(): List<Contact>` and `manualMerge(List<String>): String?` are used identically in store, screen, and tests. `Cluster(id, members, confidence, reasons)` matches `MatchTypes.kt` (`reasons: List<MatchReason>` → `emptyList()`). `Origin.MANUAL`, `Decision.PENDING`, `ReviewItem(id, origin, proposal)` match `MergeReviewTypes.kt`. `ManualMergePicker(eligible, onCancel, onCreate)` signature matches its single call site.

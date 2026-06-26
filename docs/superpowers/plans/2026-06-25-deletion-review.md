# Deletion Review (Plan 4c) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the three-pane deletion-review screen — toggle/run saved rules over the post-merge contacts, review flagged cards with reasons, approve which to delete, and apply — writing the final contact set to `AppStore` for export.

**Architecture:** `com.robsartin.contactotomy.ui.DeletionReviewStore` (StateFlow, no Compose) wraps the `core.rules` engine (ADR-0013 pattern); thin composables render it, covered by `runComposeUiTest` (ADR-0012). Builds on Plan 4a (`AppStore`, `FilePicker`) and the `core.rules` engine (Plan 3). See `docs/superpowers/specs/2026-06-25-deletion-review-design.md`. Tracking issue #16.

**Tech Stack:** Kotlin 2.0.21, Compose Desktop, kotlinx-coroutines, JUnit5, Kover (≥90 line/≥65 branch), `./gradlew` (Gradle 8.13).

---

## Conventions for every task

- Build/test with `./gradlew` (NEVER system `gradle`). Compose UI tests run headlessly via `runComposeUiTest`.
- Strict TDD: failing test first → fail → minimal impl → pass.
- Before each commit: `./gradlew spotlessApply && ./gradlew check` → BUILD SUCCESSFUL (tests + Kover 90/65 + spotlessCheck + Konsist).
- UI code only in `com.robsartin.contactotomy.ui`; no `androidx.compose` in `core`.
- Tests use the shared factory: `import com.robsartin.contactotomy.testsupport.contact` (do not redeclare it). If a test needs a field the factory lacks, add that one param to `testsupport/Contacts.kt` (YAGNI).
- Branch `16-deletion-review` (already checked out); reference issue #16 in commit messages.

Existing APIs you build on (do not change unless a task says so):

```kotlin
// core.rules
object RuleEngine { fun evaluate(contacts: List<Contact>, ruleSet: RuleSet): List<Flagged> }
fun applyDeletions(contacts: List<Contact>, approvedIds: Set<String>): List<Contact>   // top-level in core.rules
data class Rule(val name: String, val condition: Condition)
data class RuleSet(val rules: List<Rule>) { companion object }
fun RuleSet.Companion.starter(): RuleSet                 // 4 seed rules
data class RuleMatch(val ruleName: String, val reason: String)
data class Flagged(val contact: Contact, val matches: List<RuleMatch>)
object RuleStore { fun toJson(ruleSet: RuleSet): String; fun fromJson(text: String): RuleSet }
// ui (Plan 4a)
fun interface FilePicker { fun pick(): String? }
class AwtFilePicker(title: String) : FilePicker          // LOAD only today; SAVE variant added in Task 6
class AppStore(...) { val state: StateFlow<AppState>; fun next(); fun setMergedContacts(...) }
data class AppState(/* ... */ val mergedContacts: List<Contact>? = null)
```

## File Structure

- `ui/AppState.kt`, `ui/AppStore.kt` — add `finalContacts` + setter (Task 1).
- `ui/DeletionReviewTypes.kt` — `RuleToggle`, `DeletionReviewState` (Task 2).
- `ui/DeletionReviewStore.kt` — store (Tasks 3–5).
- `ui/AwtFilePicker.kt` — add SAVE mode (Task 6).
- `ui/DeletionScreen.kt` — three-pane UI (Tasks 7–8).
- `ui/App.kt` — wire `DeletionScreen` into the DELETION branch (Task 7).
- Tests mirror under `src/test/kotlin/...`.

---

### Task 1: AppStore holds the final contact set

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreFinalTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreFinalTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreFinalTest {
    @Test
    fun `setFinalContacts stores the post-deletion set`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        val final = listOf(contact("m1", given = "M"))
        store.setFinalContacts(final)
        assertEquals(final, store.state.value.finalContacts)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreFinalTest"`
Expected: FAIL — `finalContacts`/`setFinalContacts` unresolved.

- [ ] **Step 3: Add the field and setter**

In `AppState.kt`, add to `AppState`:
```kotlin
    val finalContacts: List<Contact>? = null,
```
In `AppStore.kt`, add:
```kotlin
    fun setFinalContacts(final: List<Contact>) = _state.update { it.copy(finalContacts = final) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreFinalTest"`
Expected: PASS.

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreFinalTest.kt
git commit -m "feat(ui): AppStore holds final contact set (#16)"
```

---

### Task 2: Deletion-review types

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewTypes.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewTypesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewTypesTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.Predicate
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewTypesTest {
    @Test
    fun `rule toggle defaults to enabled`() {
        val t = RuleToggle(Rule("no email", Predicate(PredicateKind.NO_EMAIL)))
        assertTrue(t.enabled)
    }

    @Test
    fun `state carries rules, flagged, approvals and totals`() {
        val state = DeletionReviewState(rules = emptyList(), totalContacts = 5)
        assertEquals(5, state.totalContacts)
        assertEquals(emptySet(), state.approvedIds)
        assertEquals(false, state.hasRun)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewTypesTest"`
Expected: FAIL — types unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewTypes.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.Flagged
import com.robsartin.contactotomy.core.rules.Rule

data class RuleToggle(val rule: Rule, val enabled: Boolean = true)

data class DeletionReviewState(
    val rules: List<RuleToggle>,
    val flagged: List<Flagged> = emptyList(),
    val approvedIds: Set<String> = emptySet(),
    val totalContacts: Int = 0,
    val hasRun: Boolean = false,
    val committed: Boolean = false,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewTypesTest"`
Expected: PASS (both).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewTypes.kt src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewTypesTest.kt
git commit -m "feat(ui): add deletion-review types (#16)"
```

---

### Task 3: DeletionReviewStore — build, toggleRule, run

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreRunTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreRunTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewStoreRunTest {
    private fun store() = DeletionReviewStore(
        listOf(
            contact("a", given = "Al", emails = listOf("al@indeed.com")),
            contact("b", given = "Bo", emails = listOf("bo@personal.com")),
        ),
    )

    @Test
    fun `seeds rules from starter, all enabled`() {
        val s = store().state.value
        assertTrue(s.rules.isNotEmpty())
        assertTrue(s.rules.all { it.enabled })
        assertEquals(2, s.totalContacts)
        assertEquals(false, s.hasRun)
    }

    @Test
    fun `run flags via enabled rules only`() {
        val store = store()
        store.run()
        val s = store.state.value
        assertTrue(s.hasRun)
        // the starter "*@indeed.com" rule flags contact a
        assertEquals(listOf("a"), s.flagged.map { it.contact.id })
    }

    @Test
    fun `disabling the matching rule yields no flags`() {
        val store = store()
        // disable every rule, then run
        store.state.value.rules.forEach { store.toggleRule(it.rule.name) }
        store.run()
        assertTrue(store.state.value.flagged.isEmpty())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewStoreRunTest"`
Expected: FAIL — `DeletionReviewStore` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.rules.RuleEngine
import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.starter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds deletion-review state: rule toggles, flagged results, and approvals. */
class DeletionReviewStore(
    private val contacts: List<Contact>,
    initialRules: RuleSet = RuleSet.starter(),
) {
    private val _state = MutableStateFlow(
        DeletionReviewState(
            rules = initialRules.rules.map { RuleToggle(it) },
            totalContacts = contacts.size,
        ),
    )
    val state: StateFlow<DeletionReviewState> = _state.asStateFlow()

    fun toggleRule(ruleName: String) = _state.update { st ->
        st.copy(rules = st.rules.map { if (it.rule.name == ruleName) it.copy(enabled = !it.enabled) else it })
    }

    fun run() {
        val enabled = RuleSet(_state.value.rules.filter { it.enabled }.map { it.rule })
        val flagged = RuleEngine.evaluate(contacts, enabled)
        _state.update { it.copy(flagged = flagged, approvedIds = emptySet(), hasRun = true) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewStoreRunTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreRunTest.kt
git commit -m "feat(ui): DeletionReviewStore build/toggle/run (#16)"
```

---

### Task 4: Approval intents + guard

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreApproveTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreApproveTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewStoreApproveTest {
    private fun ranStore(): DeletionReviewStore {
        val store = DeletionReviewStore(
            listOf(
                contact("a", given = "Al", emails = listOf("al@indeed.com")),
                contact("b", given = "Bo", emails = listOf("bo@indeed.com")),
                contact("c"), // no name, no phone -> flagged by the "no name and no phone" rule
            ),
        )
        store.run()
        return store
    }

    @Test
    fun `approve and unapprove a flagged id`() {
        val store = ranStore()
        store.approve("a")
        assertTrue("a" in store.state.value.approvedIds)
        store.unapprove("a")
        assertTrue("a" !in store.state.value.approvedIds)
    }

    @Test
    fun `approving a non-flagged id is a no-op`() {
        val store = ranStore()
        store.approve("zzz")
        assertTrue(store.state.value.approvedIds.isEmpty())
    }

    @Test
    fun `approveAllForRule approves exactly that rule's matches`() {
        val store = ranStore()
        store.approveAllForRule("old job (indeed)")
        assertEquals(setOf("a", "b"), store.state.value.approvedIds)
    }

    @Test
    fun `approveAll then clearApprovals`() {
        val store = ranStore()
        store.approveAll()
        assertEquals(store.state.value.flagged.map { it.contact.id }.toSet(), store.state.value.approvedIds)
        store.clearApprovals()
        assertTrue(store.state.value.approvedIds.isEmpty())
    }
}
```

Note: the starter rule for indeed addresses is named `"old job (indeed)"` and the
no-name rule `"no name and no phone"` — confirm the exact names in
`core/rules/StarterRules.kt` and use them verbatim.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewStoreApproveTest"`
Expected: FAIL — approve methods unresolved.

- [ ] **Step 3: Add the approval intents**

Insert into `DeletionReviewStore`:
```kotlin
    fun approve(id: String) = _state.update { st ->
        if (st.flagged.any { it.contact.id == id }) st.copy(approvedIds = st.approvedIds + id) else st
    }

    fun unapprove(id: String) = _state.update { it.copy(approvedIds = it.approvedIds - id) }

    fun approveAllForRule(ruleName: String) = _state.update { st ->
        val ids = st.flagged.filter { f -> f.matches.any { it.ruleName == ruleName } }.map { it.contact.id }
        st.copy(approvedIds = st.approvedIds + ids)
    }

    fun approveAll() = _state.update { st -> st.copy(approvedIds = st.flagged.map { it.contact.id }.toSet()) }

    fun clearApprovals() = _state.update { it.copy(approvedIds = emptySet()) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewStoreApproveTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreApproveTest.kt
git commit -m "feat(ui): deletion approval intents with flagged-only guard (#16)"
```

---

### Task 5: loadRules / rulesToJson / commit

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreCommitTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreCommitTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.RuleStore
import com.robsartin.contactotomy.core.rules.starter
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeletionReviewStoreCommitTest {
    private fun store() = DeletionReviewStore(
        listOf(
            contact("a", given = "Al", emails = listOf("al@indeed.com")),
            contact("b", given = "Bo", emails = listOf("bo@personal.com")),
        ),
    )

    @Test
    fun `commit removes approved contacts and keeps the rest`() {
        val store = store()
        store.run()
        store.approve("a")
        val result = store.commit()
        assertEquals(listOf("b"), result.map { it.id })
        assertTrue(store.state.value.committed)
    }

    @Test
    fun `rulesToJson round-trips via RuleStore`() {
        val store = store()
        val parsed = RuleStore.fromJson(store.rulesToJson())
        assertEquals(RuleSet.starter().rules.map { it.name }, parsed.rules.map { it.name })
    }

    @Test
    fun `loadRules replaces rules and clears prior results`() {
        val store = store()
        store.run()
        store.approve("a")
        val oneRuleJson = RuleStore.toJson(RuleSet(RuleSet.starter().rules.take(1)))
        store.loadRules(oneRuleJson)
        val s = store.state.value
        assertEquals(1, s.rules.size)
        assertTrue(s.flagged.isEmpty())
        assertTrue(s.approvedIds.isEmpty())
        assertEquals(false, s.hasRun)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewStoreCommitTest"`
Expected: FAIL — `commit`/`rulesToJson`/`loadRules` unresolved.

- [ ] **Step 3: Add the methods**

Insert into `DeletionReviewStore` (and add imports `com.robsartin.contactotomy.core.rules.RuleStore`, `com.robsartin.contactotomy.core.rules.applyDeletions`):
```kotlin
    fun loadRules(json: String) = _state.update {
        DeletionReviewState(
            rules = RuleStore.fromJson(json).rules.map { r -> RuleToggle(r) },
            totalContacts = contacts.size,
        )
    }

    fun rulesToJson(): String = RuleStore.toJson(RuleSet(_state.value.rules.map { it.rule }))

    fun commit(): List<Contact> {
        val result = applyDeletions(contacts, _state.value.approvedIds)
        _state.update { it.copy(committed = true) }
        return result
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionReviewStoreCommitTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStoreCommitTest.kt
git commit -m "feat(ui): deletion loadRules/rulesToJson/commit (#16)"
```

---

### Task 6: SAVE-mode file picker

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AwtFilePicker.kt`

`AwtFilePicker` is excluded from coverage (ADR-0012), so this is a small, untested platform change verified by the run-the-app step in Task 8.

- [ ] **Step 1: Add a save mode**

Replace `AwtFilePicker.kt` with:
```kotlin
package com.robsartin.contactotomy.ui

import java.awt.FileDialog
import java.awt.Frame

/** Real file chooser backed by the native AWT file dialog. LOAD by default; SAVE when [save] is true. */
class AwtFilePicker(
    private val title: String,
    private val save: Boolean = false,
) : FilePicker {
    override fun pick(): String? {
        val mode = if (save) FileDialog.SAVE else FileDialog.LOAD
        val dialog = FileDialog(null as Frame?, title, mode)
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return dir + file
    }
}
```
(Note: the existing LOAD impl filtered to `.vcf`; rules files are `.json`, so this version drops the filename filter — both vCard load and rules load/save go through the native dialog. If you prefer per-call filtering, that is a future nicety, out of scope here.)

- [ ] **Step 2: Build and gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL — `AwtFilePicker` is excluded from coverage; existing import tests (which construct `AwtFilePicker("…")`) still compile since `save` has a default.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/AwtFilePicker.kt
git commit -m "feat(ui): add SAVE mode to AwtFilePicker (#16)"
```

---

### Task 7: Deletion screen — rules + flagged + approve + commit + wire

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionScreen.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/App.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/DeletionScreenTest.kt`

- [ ] **Step 1: Write the failing UI test**

`src/test/kotlin/com/robsartin/contactotomy/ui/DeletionScreenTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DeletionScreenTest {
    private fun store() = DeletionReviewStore(
        listOf(
            contact("a", given = "Al", emails = listOf("al@indeed.com")),
            contact("b", given = "Bo", emails = listOf("bo@personal.com")),
        ),
    )

    @Test
    fun `run shows the flagged contact, approve and apply commits the deletion`() = runComposeUiTest {
        val store = store()
        var committed: List<Contact>? = null
        setContent { DeletionScreen(store, onCommit = { committed = it }) }

        onNodeWithText("Run").performClick()
        onNodeWithText("Al", substring = true).assertExists() // flagged contact appears
        onAllNodesWithText("Approve all", substring = true).onFirst().performClick()
        onNodeWithText("Apply deletions", substring = true).performClick()

        assertEquals(listOf("b"), committed?.map { it.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionScreenTest"`
Expected: FAIL — `DeletionScreen` unresolved.

- [ ] **Step 3: Write `DeletionScreen.kt`**

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
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
import java.io.File

private val NoopPicker = FilePicker { null }

@Composable
fun DeletionScreen(
    store: DeletionReviewStore,
    loadPicker: FilePicker = NoopPicker,
    savePicker: FilePicker = NoopPicker,
    onCommit: (List<Contact>) -> Unit,
) {
    val state by store.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    // flagged grouped by matched rule name
    val byRule: Map<String, List<com.robsartin.contactotomy.core.rules.Flagged>> =
        state.flagged.flatMap { f -> f.matches.map { it.ruleName to f } }.groupBy({ it.first }, { it.second })

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            // --- Left: rules ---
            Column(Modifier.fillMaxWidth(0.28f).padding(end = 8.dp)) {
                Text("Rules")
                state.rules.forEach { rt ->
                    Row {
                        Checkbox(checked = rt.enabled, onCheckedChange = { store.toggleRule(rt.rule.name) })
                        Text(rt.rule.name)
                    }
                }
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
                    CardDetail(selected)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("${state.flagged.size} flagged · ${state.approvedIds.size} approved → ${state.totalContacts - state.approvedIds.size} remain")
            Button(onClick = { onCommit(store.commit()) }, modifier = Modifier.padding(start = 8.dp)) {
                Text("Apply deletions & continue")
            }
        }
    }
}

@Composable
private fun CardDetail(f: com.robsartin.contactotomy.core.rules.Flagged) {
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
```

Note: `item { }` and `items(count: Int) { i -> }` are real `LazyListScope` members (same package as `LazyColumn`); no helper is needed. Mirror how `MergeScreen.kt` uses `LazyColumn { items(...) }`. The behavior to achieve: a header row per rule group, then one row per flagged contact in that group.

- [ ] **Step 4: Wire into `App.kt`**

Replace the DELETION stub:
```kotlin
                Screen.DELETION -> {
                    val source = state.mergedContacts ?: state.contacts
                    val deletionStore = androidx.compose.runtime.remember(source) { DeletionReviewStore(source) }
                    DeletionScreen(
                        deletionStore,
                        loadPicker = AwtFilePicker("Load rules (.json)"),
                        savePicker = AwtFilePicker("Save rules (.json)", save = true),
                    ) { final ->
                        store.setFinalContacts(final)
                        store.next()
                    }
                }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionScreenTest" --tests "com.robsartin.contactotomy.ui.AppShellTest"`
Expected: PASS. (Adjust Compose test-API imports to the installed version if a symbol like `assertExists` is a member rather than a top-level import, as in `MergeScreen` tests.)

- [ ] **Step 6: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/DeletionScreen.kt src/main/kotlin/com/robsartin/contactotomy/ui/App.kt src/test/kotlin/com/robsartin/contactotomy/ui/DeletionScreenTest.kt
git commit -m "feat(ui): deletion screen (rules/flagged/approve/commit) + wiring (#16)"
```

---

### Task 8: Detail/Load/Save coverage + run-the-app

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/DeletionScreenDetailTest.kt`

This task adds the UI tests that cover the card-detail pane and the Load/Save wiring (so coverage holds), then verifies the app by running it.

- [ ] **Step 1: Write the UI test**

`src/test/kotlin/com/robsartin/contactotomy/ui/DeletionScreenDetailTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.RuleStore
import com.robsartin.contactotomy.core.rules.starter
import com.robsartin.contactotomy.testsupport.contact
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeletionScreenDetailTest {
    private fun store() = DeletionReviewStore(
        listOf(contact("a", given = "Al", emails = listOf("al@indeed.com"))),
    )

    @Test
    fun `selecting a flagged row shows its card and reason`() = runComposeUiTest {
        val store = store()
        setContent { DeletionScreen(store, onCommit = {}) }
        onNodeWithText("Run").performClick()
        onAllNodesWithText("Al", substring = true).onFirst().performClick()
        onNodeWithText("al@indeed.com", substring = true).assertExists()
        onNodeWithText("flagged:", substring = true).assertExists()
    }

    @Test
    fun `Load reads a rules file via the picker`(@TempDir tempDir: Path) = runComposeUiTest {
        val file = tempDir.resolve("rules.json").toFile()
        file.writeText(RuleStore.toJson(RuleSet(RuleSet.starter().rules.take(1))))
        val store = store()
        setContent { DeletionScreen(store, loadPicker = FilePicker { file.absolutePath }, onCommit = {}) }
        onNodeWithText("Load", substring = true).performClick()
        assertTrue(store.state.value.rules.size == 1)
    }
}
```
Note: `assertExists()` comes from the Compose test API (a member of `SemanticsNodeInteraction`), not `kotlin.test` — use the same import style as the other passing UI tests (`MergeScreenListTest`).

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.DeletionScreenDetailTest"`
Expected: PASS (both). Adjust Compose test-API imports to the installed version as needed.

- [ ] **Step 3: Full gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL — coverage ≥90/65 (the detail + load paths are now exercised). If branch coverage dips below 65, add a focused UI assertion (e.g. toggling a rule off then Run shows "No matches") rather than lowering the floor.

- [ ] **Step 4: Run the app (controller-verified)**

Run: `./gradlew run`
Expected: import a `.vcf` (or the fixture twice), advance through Merge, reach Deletion: the three panes render; Run flags contacts; approving + "Apply deletions & continue" advances to the Export stub. Screenshot/notes for the PR. (The controller runs this; it needs a display.)

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/com/robsartin/contactotomy/ui/DeletionScreenDetailTest.kt
git commit -m "test(ui): cover deletion detail pane and rules Load (#16)"
```

---

## Self-Review

**Spec coverage:**
- §3 data flow (post-merge contacts; RuleEngine/RuleStore/applyDeletions; commit → AppStore.finalContacts): Tasks 1, 3, 5, 7. ✓
- §4 store (seed from starter as toggles; toggleRule; run clears approvals; approve/unapprove/approveAllForRule/approveAll/clearApprovals; approval guard; loadRules/rulesToJson; commit): Tasks 3–5. ✓
- §5 UI (three panes; rules checklist + Load/Save/Run; flagged grouped by rule + approve-all; card detail + reasons; summary; Apply advances): Tasks 7–8. ✓
- §6 testing (store unit tests + Compose UI tests + run-the-app): Tasks 1–8. ✓
- §7 scope (SAVE FilePicker; finalContacts; no in-app rule editing; export deferred): Tasks 6, 1; export untouched. ✓

**Placeholder scan:** No TBD/TODO; every code step is complete and self-contained. `LazyColumn` rows use the real `LazyListScope.item`/`items(count)` members (mirroring `MergeScreen.kt`); Compose `assertExists()` is a member call, noted where used.

**Type consistency:** `DeletionReviewStore(contacts, initialRules)` with intents `toggleRule`, `run`, `approve`/`unapprove`/`approveAllForRule`/`approveAll`/`clearApprovals`, `loadRules(json)`/`rulesToJson()`, `commit(): List<Contact>` are consistent across Tasks 3–8. `DeletionReviewState(rules, flagged, approvedIds, totalContacts, hasRun, committed)` and `RuleToggle(rule, enabled)` match Tasks 2–8. `DeletionScreen(store, loadPicker, savePicker, onCommit)` matches its `App.kt` call and tests. `AppStore.setFinalContacts` / `AppState.finalContacts` consistent (Task 1). Starter rule names referenced in tests must match `StarterRules.kt` verbatim (Task 4 note).

**Risk notes:** (1) Compose test-API symbols vary by version — adjust imports as the merge tests did. (2) `LazyColumn` group rendering: mirror `MergeScreen.kt`'s `items` usage exactly; don't invent a helper. (3) Confirm starter rule names (`"old job (indeed)"`, `"no name and no phone"`) in `StarterRules.kt` before writing the Task 4 test assertions.

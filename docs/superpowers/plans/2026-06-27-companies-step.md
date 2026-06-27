# Companies Review Step Implementation Plan (#37)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reviewed `COMPANIES` wizard step (after Merge) where the user confirms which standalone cards are companies; confirmed cards get name→org (when org is blank) and the name cleared.

**Architecture:** A pure `core` normalizer + a `CompanyReviewStore` (high-precision suspects pre-marked) + a `CompanyScreen` listing all post-merge contacts. A new `Screen.COMPANIES` between MERGE and DELETION threads `companyContacts` through `AppState` into Deletion. No matcher/merger/exporter change.

**Tech Stack:** Kotlin, Compose Desktop Material (1.7.3), `runComposeUiTest`, Kover (line ≥90 / branch ≥70), Spotless/ktlint, Konsist.

## Global Constraints
- No `core` change beyond `core/company/` (detector helper + normalizer); Konsist keeps `core` UI-free.
- Presentation/flow only; matcher/merger/apply/exporter untouched.
- `./gradlew check` stays green (line ≥90 / branch ≥70, Spotless, Konsist). macOS: plain `./gradlew test` (no xvfb). Compose compile is slow — normal.
- Reuse shared `displayName(ContactName)` (ui) and the new `companyNameText(ContactName)` (core); #30 theme components exist.

Branch: `37-companies-step` (off `main`). Issue: #37. Spec: `docs/superpowers/specs/2026-06-27-companies-step-design.md`.

## File Structure
- Modify `core/company/CompanyNameDetector.kt` — extract `companyNameText`, add `isHighPrecision`.
- Create `core/company/CompanyNormalizer.kt` — `markAsCompany`.
- Create `ui/CompanyReviewStore.kt` — store + `CompanyReviewState`.
- Create `ui/CompanyScreen.kt` — the screen.
- Modify `ui/AppState.kt` — `Screen.COMPANIES`, `companyContacts`, `workingContacts`.
- Modify `ui/AppStore.kt` — `setCompanyContacts`.
- Modify `ui/App.kt` — hoist company store, deletion source, Next handling, render, StepIndicator.
- Tests: `CompanyNameDetectorTest`, `CompanyNormalizerTest`, `CompanyReviewStoreTest`, `CompanyScreenTest`, `AppNavigationTest` (update), `AppFlowTest` (update + new case) + fixture.

---

### Task 1: Core — `companyNameText` + `isHighPrecision`

**Files:** Modify `src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt`; Test `src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetectorTest.kt`

**Interfaces — Produces:**
- `fun companyNameText(name: ContactName): String` (top-level, package `core.company`)
- `fun CompanyNameDetector.isHighPrecision(name: ContactName): Boolean`

- [ ] **Step 1: Write the failing test**

Append to `CompanyNameDetectorTest.kt`:

```kotlin
    @Test
    fun `companyNameText prefers formatted then given-family`() {
        assertEquals("Acme Inc", companyNameText(ContactName(formatted = "Acme Inc")))
        assertEquals("Jane Smith", companyNameText(ContactName(given = "Jane", family = "Smith")))
        assertEquals("", companyNameText(ContactName()))
    }

    @Test
    fun `isHighPrecision is true for strong signals only`() {
        assertEquals(true, CompanyNameDetector.isHighPrecision(named(formatted = "Acme Inc")))     // LEGAL_SUFFIX
        assertEquals(true, CompanyNameDetector.isHighPrecision(named(formatted = "Smith & Sons"))) // AMPERSAND
        assertEquals(true, CompanyNameDetector.isHighPrecision(named(formatted = "Round Rock ISD")))// KEYWORD
        assertEquals(false, CompanyNameDetector.isHighPrecision(named(formatted = "ACME")))         // WEAK
        assertEquals(false, CompanyNameDetector.isHighPrecision(named(given = "Dave")))             // WEAK
        assertEquals(false, CompanyNameDetector.isHighPrecision(named(given = "Jane", family = "Smith"))) // null
    }
```

(`named(...)` helper and `assertEquals` already exist in this test file; add `import com.robsartin.contactotomy.core.model.ContactName` if not present.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNameDetectorTest"`
Expected: FAIL — unresolved `companyNameText` / `isHighPrecision`.

- [ ] **Step 3: Implement**

In `CompanyNameDetector.kt`, add a top-level function (above the `object`):

```kotlin
/** The company string a name field holds: formatted if present, else given + family. */
fun companyNameText(name: ContactName): String =
    name.formatted?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(name.given, name.family).joinToString(" ")
```

Refactor the start of `detect` to reuse it (behavior unchanged):

```kotlin
    fun detect(name: ContactName): CompanySignal? {
        val display = companyNameText(name)
        if (display.isBlank()) return null
```

Add to the `CompanyNameDetector` object:

```kotlin
    /** True only for the strong signals safe to auto-pre-check (never WEAK). */
    fun isHighPrecision(name: ContactName): Boolean =
        when (detect(name)) {
            CompanySignal.LEGAL_SUFFIX, CompanySignal.AMPERSAND, CompanySignal.KEYWORD -> true
            CompanySignal.WEAK, null -> false
        }
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNameDetectorTest"`
Expected: PASS (new + existing detector tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetectorTest.kt
git commit -m "feat(core): companyNameText + isHighPrecision helpers (#37)"
```

---

### Task 2: Core — `CompanyNormalizer.markAsCompany`

**Files:** Create `src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNormalizer.kt`; Test `src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNormalizerTest.kt`

**Interfaces:**
- Consumes: `companyNameText` (Task 1).
- Produces: `object CompanyNormalizer { fun markAsCompany(contact: Contact): Contact }`.

- [ ] **Step 1: Write the failing test**

Create `CompanyNormalizerTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanyNormalizerTest {
    @Test
    fun `blank org gets the name promoted and the name cleared`() {
        val c = contact("a", given = "Round", family = "ISD", middle = "Rock") // name "Round Rock ISD"? see note
        val out = CompanyNormalizer.markAsCompany(c.copy(name = ContactName(formatted = "Round Rock ISD")))
        assertEquals("Round Rock ISD", out.org)
        assertEquals(ContactName(), out.name)
    }

    @Test
    fun `existing org is kept and only the name is cleared`() {
        val c = contact("a", given = "Acme", family = "Inc", org = "Acme Incorporated")
        val out = CompanyNormalizer.markAsCompany(c)
        assertEquals("Acme Incorporated", out.org)
        assertEquals(ContactName(), out.name)
    }
}
```

(Use `ContactName(formatted = ...)` for the first case so `companyNameText` yields exactly "Round Rock ISD".)

- [ ] **Step 2: Run → FAIL** (`CompanyNormalizer` unresolved)

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNormalizerTest"`

- [ ] **Step 3: Implement**

Create `CompanyNormalizer.kt`:

```kotlin
package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName

/** Turns a contact into a company-only card: name -> org (when org is blank), name cleared. */
object CompanyNormalizer {
    fun markAsCompany(contact: Contact): Contact =
        if (contact.org.isNullOrBlank()) {
            contact.copy(org = companyNameText(contact.name), name = ContactName())
        } else {
            contact.copy(name = ContactName())
        }
}
```

- [ ] **Step 4: Run → PASS**

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNormalizer.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNormalizerTest.kt
git commit -m "feat(core): CompanyNormalizer.markAsCompany (#37)"
```

---

### Task 3: Store — `CompanyReviewStore`

**Files:** Create `src/main/kotlin/com/robsartin/contactotomy/ui/CompanyReviewStore.kt`; Test `src/test/kotlin/com/robsartin/contactotomy/ui/CompanyReviewStoreTest.kt`

**Interfaces:**
- Consumes: `CompanyNameDetector.isHighPrecision` (Task 1), `CompanyNormalizer.markAsCompany` (Task 2), `companyNameText` (Task 1).
- Produces: `class CompanyReviewStore(contacts: List<Contact>)` with `val state: StateFlow<CompanyReviewState>`, `fun toggle(id: String)`, `fun commit(): List<Contact>`, `fun listed(): List<Contact>`; `data class CompanyReviewState(val markedIds: Set<String>)`.

- [ ] **Step 1: Write the failing test**

Create `CompanyReviewStoreTest.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanyReviewStoreTest {
    private val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc")) // LEGAL_SUFFIX
    private val jane = contact("jane", given = "Jane", family = "Smith")                // null -> not a suspect
    private val bottle = contact("bottle", given = "Blue", family = "Bottle")           // null -> not a suspect

    @Test
    fun `high-precision suspects are pre-marked, others are not`() {
        val store = CompanyReviewStore(listOf(acme, jane))
        assertTrue("acme" in store.state.value.markedIds)
        assertFalse("jane" in store.state.value.markedIds)
    }

    @Test
    fun `toggle adds then removes a mark`() {
        val store = CompanyReviewStore(listOf(jane))
        store.toggle("jane")
        assertTrue("jane" in store.state.value.markedIds)
        store.toggle("jane")
        assertFalse("jane" in store.state.value.markedIds)
    }

    @Test
    fun `commit normalizes only marked contacts`() {
        val store = CompanyReviewStore(listOf(acme, jane))
        val result = store.commit().associateBy { it.id }
        assertEquals("Acme Inc", result["acme"]?.org)
        assertEquals(ContactName(), result["acme"]?.name)
        assertEquals("Jane", result["jane"]?.name?.given) // untouched
    }

    @Test
    fun `manually marking a detector-missed card normalizes it`() {
        val store = CompanyReviewStore(listOf(bottle))
        store.toggle("bottle")
        val out = store.commit().single()
        assertEquals("Blue Bottle", out.org)
        assertEquals(ContactName(), out.name)
    }

    @Test
    fun `listed omits nameless cards`() {
        val nameless = contact("x").copy(name = ContactName())
        val store = CompanyReviewStore(listOf(acme, nameless))
        assertEquals(listOf("acme"), store.listed().map { it.id })
    }
}
```

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.CompanyReviewStoreTest"`

- [ ] **Step 3: Implement**

Create `CompanyReviewStore.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.company.CompanyNameDetector
import com.robsartin.contactotomy.core.company.CompanyNormalizer
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CompanyReviewState(val markedIds: Set<String> = emptySet())

/** Review which standalone contacts are companies; high-precision suspects start marked. */
class CompanyReviewStore(private val contacts: List<Contact>) {
    private val _state =
        MutableStateFlow(
            CompanyReviewState(
                markedIds = contacts.filter { CompanyNameDetector.isHighPrecision(it.name) }.map { it.id }.toSet(),
            ),
        )
    val state: StateFlow<CompanyReviewState> = _state.asStateFlow()

    /** Contacts with a non-blank name — the only ones worth marking. */
    fun listed(): List<Contact> = contacts.filter { companyNameText(it.name).isNotBlank() }

    fun toggle(id: String) =
        _state.update { st ->
            st.copy(markedIds = if (id in st.markedIds) st.markedIds - id else st.markedIds + id)
        }

    fun commit(): List<Contact> {
        val marked = _state.value.markedIds
        return contacts.map { if (it.id in marked) CompanyNormalizer.markAsCompany(it) else it }
    }
}
```

- [ ] **Step 4: Run → PASS**

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/CompanyReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/CompanyReviewStoreTest.kt
git commit -m "feat(ui): CompanyReviewStore (suspects pre-marked, commit normalizes) (#37)"
```

---

### Task 4: Screen — `CompanyScreen`

**Files:** Create `src/main/kotlin/com/robsartin/contactotomy/ui/CompanyScreen.kt`; Test `src/test/kotlin/com/robsartin/contactotomy/ui/CompanyScreenTest.kt`

**Interfaces:**
- Consumes: `CompanyReviewStore` (Task 3), `companyNameText` (Task 1), `displayName` (ui), `SourceBadge`/`SectionHeader` (ui.components).
- Produces: `@Composable fun CompanyScreen(store: CompanyReviewStore)`.

- [ ] **Step 1: Write the failing test**

Create `CompanyScreenTest.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompanyScreenTest {
    private val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc"))
    private val jane = contact("jane", given = "Jane", family = "Smith")

    @Test
    fun `a suspect row shows the to-org hint (pre-marked)`() =
        runComposeUiTest {
            setContent { CompanyScreen(CompanyReviewStore(listOf(acme, jane))) }
            onNodeWithText("→ org: Acme Inc", substring = true).assertIsDisplayed()
        }

    @Test
    fun `toggling a row updates the store`() =
        runComposeUiTest {
            val store = CompanyReviewStore(listOf(jane))
            setContent { CompanyScreen(store) }
            onNodeWithText("Jane Smith", substring = true).performClick()
            assertTrue("jane" in store.state.value.markedIds)
        }

    @Test
    fun `filter narrows the list`() =
        runComposeUiTest {
            setContent { CompanyScreen(CompanyReviewStore(listOf(acme, jane))) }
            onNodeWithText("Filter").performTextInput("Acme")
            onNodeWithText("Acme Inc", substring = true).assertIsDisplayed()
            // "Jane Smith" filtered out
            androidx.compose.ui.test
                .onAllNodesWithText("Jane Smith", substring = true)
                .assertCountEquals(0)
        }
}
```

(Add `import androidx.compose.ui.test.assertCountEquals` if your style prefers it over the fully-qualified call.)

- [ ] **Step 2: Run → FAIL**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.CompanyScreenTest"`

- [ ] **Step 3: Implement**

Create `CompanyScreen.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.components.SourceBadge
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun CompanyScreen(store: CompanyReviewStore) {
    val state by store.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val all = store.listed()
    val rows = all.filter { query.isBlank() || displayName(it.name).contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        SectionHeader("Mark companies")
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Filter") })
        Text("${state.markedIds.size} of ${all.size} marked as companies", Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { c ->
                val marked = c.id in state.markedIds
                Row(
                    Modifier.fillMaxWidth().clickable { store.toggle(c.id) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = marked, onCheckedChange = null)
                    SourceBadge(c.source)
                    Text("  ${displayName(c.name)}")
                    if (marked) {
                        Text(
                            "  → org: ${companyNameText(c.name)}",
                            color = appColors.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.CompanyScreenTest"`

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/CompanyScreen.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/CompanyScreenTest.kt
git commit -m "feat(ui): CompanyScreen lists contacts, suspects pre-checked (#37)"
```

---

### Task 5: Wizard wiring — insert `COMPANIES`, thread `companyContacts`, fix nav/flow tests

**Files:** Modify `ui/AppState.kt`, `ui/AppStore.kt`, `ui/App.kt`; Modify tests `ui/AppNavigationTest.kt`, `ui/AppFlowTest.kt`

**Interfaces:**
- Consumes: `CompanyReviewStore` (Task 3), `CompanyScreen` (Task 4).
- Produces: `Screen.COMPANIES`; `AppState.companyContacts`; `AppStore.setCompanyContacts`.

- [ ] **Step 1: AppState — enum value, field, workingContacts**

In `AppState.kt`:
- `enum class Screen { IMPORT, MERGE, COMPANIES, DELETION, EXPORT }`
- add to `AppState`: `val companyContacts: List<Contact>? = null,` (after `mergedContacts`)
- update `workingContacts`:
```kotlin
fun workingContacts(state: AppState): List<com.robsartin.contactotomy.core.model.Contact> =
    state.finalContacts ?: state.companyContacts ?: state.mergedContacts ?: state.contacts
```

- [ ] **Step 2: AppStore — setter**

In `AppStore.kt`, after `setMergedContacts`:
```kotlin
    fun setCompanyContacts(companies: List<Contact>) = _state.update { it.copy(companyContacts = companies) }
```

- [ ] **Step 3: App.kt — hoist store, deletion source, Next, render, label**

In `App.kt`:
- After the `mergeStore` line, add and update the deletion source:
```kotlin
            val companySource = state.mergedContacts ?: state.contacts
            val companyStore = remember(companySource) { CompanyReviewStore(companySource) }
            val deletionSource = state.companyContacts ?: state.mergedContacts ?: state.contacts
```
- In the Next `onClick` `when`, add a `COMPANIES` branch (before `else`):
```kotlin
                            Screen.COMPANIES -> {
                                store.setCompanyContacts(companyStore.commit())
                                store.next()
                            }
```
- In the `when (state.screen)` render block, add:
```kotlin
                Screen.COMPANIES ->
                    CompanyScreen(companyStore)
```
- In `StepIndicator`'s `labels` list, add `Screen.COMPANIES to "Companies"` between Merge and Deletion.

(`nextEnabled` needs no change — COMPANIES falls into `else -> true`.)

- [ ] **Step 4: Update `AppNavigationTest`**

- Rename `Next on Merge commits the merge and advances to Deletion` → `...advances to Companies`, and change its final assertion `assertEquals(Screen.DELETION, ...)` to `assertEquals(Screen.COMPANIES, ...)` (keep the `mergedContacts?.size == 1` assertion).
- Add a new test:
```kotlin
    @Test
    fun `Next on Companies commits and advances to Deletion`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.COMPANIES)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            onNodeWithText("Next").performClick()
            assertNotNull(store.state.value.companyContacts)
            assertEquals(Screen.DELETION, store.state.value.screen)
        }
```
(The `Next on Deletion...` test uses `goTo(Screen.DELETION)` and is unaffected.)

- [ ] **Step 5: Update existing `AppFlowTest` cases (one extra Next)**

Inserting `COMPANIES` means every case that clicked Next to go Merge→Deletion now lands on Companies first. In EACH of these `AppFlowTest` cases, immediately AFTER the `Next` line commented `// commit merge -> Deletion` (or the equivalent Merge-advancing Next), insert one more:
```kotlin
            onNodeWithText("Next").performClick() // Companies (pass-through) -> Deletion
```
and update the preceding comment to `// commit merge -> Companies`. Affected cases:
- `accepting a merge reduces the exported set to four`
- `no accept leaves all five contacts` (its second `Next` is Merge→Companies; insert the Companies→Deletion Next before the deletion Next)
- `manual merge combines two un-clustered cards across the whole flow`
- `manual merge promotes a company name into org and exports it`
- `company-only cluster exports ORG with no name`
- `deletion path removes an approved card` (insert the extra Next before the `Run` click)

Do not change any assertions. Run the suite after editing.

- [ ] **Step 6: Run the affected suites → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppNavigationTest" --tests "com.robsartin.contactotomy.ui.AppFlowTest" --tests "com.robsartin.contactotomy.ui.AppShellTest"`
Expected: PASS. Then `./gradlew test` (full suite) green. `./gradlew spotlessCheck` (apply + amend if needed).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt \
        src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt \
        src/main/kotlin/com/robsartin/contactotomy/ui/App.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/AppNavigationTest.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt
git commit -m "feat(ui): insert COMPANIES wizard step; thread companyContacts (#37)"
```

---

### Task 6: End-to-end — standalone company normalized through the Companies step

**Files:** Create `src/test/resources/fixtures/lone-company.vcf`; Modify `ui/AppFlowTest.kt`

- [ ] **Step 1: Fixture**

Create `src/test/resources/fixtures/lone-company.vcf` (one un-clustered company card, no org):

```
BEGIN:VCARD
VERSION:3.0
N:ISD;Round;Rock;;
FN:Round Rock ISD
EMAIL;TYPE=INTERNET:info@rrisd.example
END:VCARD
```

- [ ] **Step 2: Test**

Add to `AppFlowTest` (imports `VcfExporter`, `ContactName`, `assertFalse` already added by #36; verify and add if missing):

```kotlin
    @Test
    fun `standalone company is normalized by the Companies step and exported as org only`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("lone-company.vcf"), Source.APPLE) }
            assertEquals(1, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge (no clusters)
            onNodeWithText("Next").performClick() // commit merge (no accepts) -> Companies
            // "Round Rock ISD" is high-precision (ISD) => pre-checked on the Companies step
            onNodeWithText("→ org: Round Rock ISD", substring = true).assertIsDisplayed()
            onNodeWithText("Next").performClick() // commit Companies -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertEquals(1, final.size)
            assertEquals("Round Rock ISD", final.single().org)
            assertEquals(ContactName(), final.single().name)

            val vcard = VcfExporter().export(final)
            assertTrue(vcard.contains("ORG:Round Rock ISD"), "expected ORG:\n$vcard")
            assertFalse(vcard.contains("FN:Round Rock ISD"), "company-only card should have no FN:\n$vcard")
        }
```

- [ ] **Step 3: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppFlowTest"`
Expected: PASS. If "→ org: Round Rock ISD" isn't shown, the card wasn't pre-checked — confirm `isHighPrecision` flags ISD (Task 1) and the card survived merge as a singleton.

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/fixtures/lone-company.vcf src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt
git commit -m "test(ui): e2e standalone company normalized via Companies step (#37)"
```

---

### Task 7: Full gate, push, PR

- [ ] **Step 1: Gate** — `./gradlew check` → BUILD SUCCESSFUL (tests, Kover 90/70, Spotless, Konsist). `spotlessApply` + amend if Spotless flags.
- [ ] **Step 2: Push** — `git push -u origin 37-companies-step`
- [ ] **Step 3: PR**

```bash
gh pr create --base main --head 37-companies-step \
  --title "Companies review step: mark standalone cards as companies (Closes #37)" \
  --body "Implements #37 per docs/superpowers/specs/2026-06-27-companies-step-design.md. New COMPANIES wizard step after Merge: lists all post-merge contacts with high-precision company suspects pre-checked; the user ticks/unticks any card (incl. detector-missed); confirmed cards get name->org (when org blank) + name cleared, applied after matching. Pure core CompanyNormalizer + CompanyReviewStore + CompanyScreen; no matcher/merger/exporter change. Closes #37."
```

- [ ] **Step 4: Watch CI** — `gh pr checks <PR#> --watch`; on green hand off for the user's run-the-app visual review.

---

## Self-Review

**Spec coverage:** §2 new step after Merge → Task 5; all-contacts-listed/suspects-pre-checked → Tasks 3,4; never-WEAK pre-check → Task 1 `isHighPrecision`; action (name→org when blank else keep, always clear) → Task 2; filter + "→ org" hint → Task 4; data flow `companyContacts`/`workingContacts`/deletion source → Task 5; tests incl. e2e + nav churn → Tasks 1–6. ✓
**Placeholder scan:** none — full code per step; the nav/flow test churn (Task 5 Steps 4–5) lists each affected case with the exact insertion. ✓
**Type consistency:** `companyNameText(ContactName): String`, `CompanyNameDetector.isHighPrecision(ContactName): Boolean`, `CompanyNormalizer.markAsCompany(Contact): Contact`, `CompanyReviewStore(List<Contact>)` with `state/toggle/commit/listed`, `CompanyReviewState(markedIds)`, `Screen.COMPANIES`, `AppState.companyContacts`, `setCompanyContacts` — all used consistently across tasks and call sites. `CompanyScreen(store)` matches its render site in App.kt and its tests. ✓

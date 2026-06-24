# App Shell + Import (Plan 4a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Contactotomy Compose Desktop app — wizard shell, a StateFlow-based `AppStore`, and a working Import screen (per-source pickers) — with the Compose UI test harness and the coverage bar raised to 90/65.

**Architecture:** New `com.robsartin.contactotomy.ui` package (depends on `core`; Konsist keeps `core` UI-free). Logic lives in a framework-free `AppStore` (unit-tested); composables are thin and covered by headless `runComposeUiTest`. Only `MainKt` and the real AWT file dialog are excluded from coverage. See `docs/superpowers/specs/2026-06-24-app-shell-import-design.md` and ADR-0006/0012. Tracking issue #8.

**Tech Stack:** Kotlin 2.0.21, Compose Desktop (`org.jetbrains.compose` ~1.7.x) + Compose compiler plugin, kotlinx-coroutines (StateFlow), JUnit5, Kover, `./gradlew` (Gradle 8.13).

---

## Conventions for every task

- Build/test with `./gradlew` (NEVER system `gradle`).
- Strict TDD: failing test first → fail → minimal impl → pass.
- Before each commit run `./gradlew spotlessApply && ./gradlew check` and ensure BUILD SUCCESSFUL (tests + Kover line≥90/branch≥65 + spotlessCheck + Konsist).
- No `androidx.compose`/UI imports in `core` (Konsist enforces this). UI code goes only in `com.robsartin.contactotomy.ui`.
- Branch `8-app-shell-import` (already checked out); commit there.

Reference — existing `core` model (Plan 1):

```kotlin
// com.robsartin.contactotomy.core.model
enum class Source { APPLE, GOOGLE, FILE }
data class Contact(val id: String, val source: Source, /* ... */ val rawVCard: String)
// com.robsartin.contactotomy.core.importer.VcfImporter(source: Source).import(vcfText: String): List<Contact>
```

## File Structure

- `build.gradle.kts` — Compose plugins/deps, application entry, raised Kover bounds + UI excludes (Task 1).
- `.github/workflows/ci.yml` — run `check` under `xvfb` on Linux (Task 1).
- `src/main/kotlin/com/robsartin/contactotomy/ui/Main.kt` — `main()` entry (Task 1, wired in Task 7).
- `.../ui/AppState.kt` — `Screen`, `ImportedFile`, `AppState` (Task 2).
- `.../ui/AppStore.kt` — state holder + intents (Tasks 2–4).
- `.../ui/FilePicker.kt` — `FilePicker` interface (Task 3); `AwtFilePicker` real impl (Task 7).
- `.../ui/App.kt` — wizard shell composable + stub screens (Task 5).
- `.../ui/ImportScreen.kt` — import screen composable (Task 6).
- Tests mirror these under `src/test/kotlin/...`.

---

### Task 1: Compose Desktop build setup + headless UI-test harness + raised coverage bar

**Files:**
- Modify: `build.gradle.kts`
- Modify: `.github/workflows/ci.yml`
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/Main.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/ComposeHarnessTest.kt`

- [ ] **Step 1: Add Compose plugins, dependencies, and application entry to `build.gradle.kts`**

In the `plugins { }` block add:
```kotlin
id("org.jetbrains.compose") version "1.7.3"
id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
```
In `dependencies { }` add:
```kotlin
implementation(compose.desktop.currentOs)
testImplementation(compose.desktop.uiTestJUnit4)
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
```
Add an application block (top level):
```kotlin
compose.desktop {
    application {
        mainClass = "com.robsartin.contactotomy.ui.MainKt"
    }
}
```
Verify these versions resolve (Compose 1.7.x is compatible with Kotlin 2.0.21 and the Compose compiler plugin 2.0.21). If a version doesn't resolve, pick the nearest compatible and note it.

- [ ] **Step 2: Raise the Kover bounds and add UI exclusions**

In the existing `kover { reports { ... } }` config:
- Change the verify bounds from line 80 → **90** and branch 60 → **65** (leave the rule structure as-is, only the `minValue`s change).
- Add to the existing `filters { excludes { classes(...) } }` list: `"com.robsartin.contactotomy.ui.MainKt"` and `"com.robsartin.contactotomy.ui.AwtFilePicker"` (the real file-dialog adapter, added in Task 7), alongside the existing `*$serializer` entry.

- [ ] **Step 3: Make CI run the UI tests headlessly**

In `.github/workflows/ci.yml`, replace the single check step with an xvfb-wrapped run on the Linux runner:
```yaml
      - name: Install xvfb
        run: sudo apt-get update && sudo apt-get install -y xvfb
      - name: Check (tests, coverage, format, Konsist)
        run: xvfb-run -a ./gradlew check
```
(Keep the checkout / setup-java / setup-gradle steps unchanged.)

- [ ] **Step 4: Create a minimal entry point**

`src/main/kotlin/com/robsartin/contactotomy/ui/Main.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Contactotomy") {
        MaterialTheme { Text("Contactotomy") }
    }
}
```

- [ ] **Step 5: Write a sanity UI test (proves the headless harness works)**

`src/test/kotlin/com/robsartin/contactotomy/ui/ComposeHarnessTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposeHarnessTest {
    @Test
    fun `compose ui test harness renders text`() = runComposeUiTest {
        setContent { Text("hello-contactotomy") }
        onNodeWithText("hello-contactotomy").assertIsDisplayed()
    }
}
```

- [ ] **Step 6: Run the UI test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.ComposeHarnessTest"`
Expected: PASS. (On macOS this runs without xvfb. If it fails to find a Compose UI-test symbol, the `compose.desktop.uiTestJUnit4` dependency or `@OptIn(ExperimentalTestApi::class)` is wrong — fix to the API that the resolved Compose version exposes; the goal is a passing headless render+assert.)

- [ ] **Step 7: Run the full gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL — koverVerify now enforces line≥90/branch≥65 and still passes (existing coverage ~93.7/71.9; `MainKt` excluded).

- [ ] **Step 8: Commit**

```bash
git add build.gradle.kts .github/workflows/ci.yml src/main/kotlin/com/robsartin/contactotomy/ui/Main.kt src/test/kotlin/com/robsartin/contactotomy/ui/ComposeHarnessTest.kt
git commit -m "build: add Compose Desktop + UI test harness, raise coverage to 90/65 (#8)"
```

---

### Task 2: AppState and AppStore navigation

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreNavigationTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreNavigationTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreNavigationTest {
    private fun storeWithContacts(n: Int): AppStore {
        val store = AppStore(parse = { _, _ -> (1..n).map {
            Contact(id = "c$it", source = Source.APPLE, name = ContactName(given = "C$it"), rawVCard = "")
        } })
        if (n > 0) kotlinx.coroutines.runBlocking { store.importFile("f.vcf", Source.APPLE) }
        return store
    }

    @Test
    fun `next is blocked on import with no contacts`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        store.next()
        assertEquals(Screen.IMPORT, store.state.value.screen)
    }

    @Test
    fun `next advances and back returns once there are contacts`() {
        val store = storeWithContacts(2)
        store.next()
        assertEquals(Screen.MERGE, store.state.value.screen)
        store.back()
        assertEquals(Screen.IMPORT, store.state.value.screen)
    }

    @Test
    fun `goTo jumps directly and back from import is a no-op`() {
        val store = storeWithContacts(1)
        store.goTo(Screen.EXPORT)
        assertEquals(Screen.EXPORT, store.state.value.screen)
        store.goTo(Screen.IMPORT)
        store.back()
        assertEquals(Screen.IMPORT, store.state.value.screen)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreNavigationTest"`
Expected: FAIL — `AppStore`/`AppState`/`Screen` unresolved.

- [ ] **Step 3: Write `AppState.kt`**

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source

enum class Screen { IMPORT, MERGE, DELETION, EXPORT }

data class ImportedFile(val path: String, val source: Source, val count: Int)

data class AppState(
    val screen: Screen = Screen.IMPORT,
    val imported: List<ImportedFile> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val importing: Boolean = false,
    val error: String? = null,
)
```

- [ ] **Step 4: Write `AppStore.kt` (navigation + skeleton; import added in Task 3)**

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class AppStore(
    private val parse: (String, Source) -> List<Contact> = { path, source ->
        com.robsartin.contactotomy.core.importer.VcfImporter(source).import(java.io.File(path).readText())
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var importCounter = 0

    fun next() {
        val s = _state.value
        if (s.screen == Screen.IMPORT && s.contacts.isEmpty()) return
        val order = Screen.entries
        val i = order.indexOf(s.screen)
        if (i < order.lastIndex) _state.update { it.copy(screen = order[i + 1]) }
    }

    fun back() {
        val order = Screen.entries
        val i = order.indexOf(_state.value.screen)
        if (i > 0) _state.update { it.copy(screen = order[i - 1]) }
    }

    fun goTo(screen: Screen) = _state.update { it.copy(screen = screen) }

    suspend fun importFile(path: String, source: Source) {
        _state.update { it.copy(importing = true, error = null) }
        val result = runCatching { withContext(ioDispatcher) { parse(path, source) } }
        result.onSuccess { parsed ->
            val n = importCounter++
            val namespaced = parsed.map { it.copy(id = "imp$n:${it.id}") }
            _state.update { st ->
                st.copy(
                    importing = false,
                    imported = st.imported + ImportedFile(path, source, parsed.size),
                    contacts = st.contacts + namespaced,
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(importing = false, error = e.message ?: "Import failed") }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreNavigationTest"`
Expected: PASS (all 3).

- [ ] **Step 6: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreNavigationTest.kt
git commit -m "feat(ui): add AppState and AppStore navigation (#8)"
```

---

### Task 3: Import logic (FilePicker + namespaced import)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/FilePicker.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreImportTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreImportTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppStoreImportTest {
    private fun fakeContacts(vararg ids: String, source: Source = Source.APPLE) =
        ids.map { Contact(id = it, source = source, name = ContactName(given = it), rawVCard = "") }

    @Test
    fun `import appends contacts and a file summary, namespacing ids`() = runTest {
        val store = AppStore(parse = { _, src -> fakeContacts("a", "b", source = src) }, ioDispatcher = Dispatchers.Unconfined)
        store.importFile("apple.vcf", Source.APPLE)
        val s = store.state.value
        assertEquals(listOf("imp0:a", "imp0:b"), s.contacts.map { it.id })
        assertEquals(2, s.imported.single().count)
        assertEquals(Source.APPLE, s.imported.single().source)
        assertEquals(false, s.importing)
    }

    @Test
    fun `two imports keep ids unique across files`() = runTest {
        val store = AppStore(parse = { _, src -> fakeContacts("x", source = src) }, ioDispatcher = Dispatchers.Unconfined)
        store.importFile("apple.vcf", Source.APPLE)
        store.importFile("google.vcf", Source.GOOGLE)
        assertEquals(listOf("imp0:x", "imp1:x"), store.state.value.contacts.map { it.id })
    }

    @Test
    fun `import failure sets error without throwing`() = runTest {
        val store = AppStore(parse = { _, _ -> error("bad file") }, ioDispatcher = Dispatchers.Unconfined)
        store.importFile("broken.vcf", Source.APPLE)
        val s = store.state.value
        assertEquals("bad file", s.error)
        assertTrue(s.contacts.isEmpty())
        assertEquals(false, s.importing)
    }

    @Test
    fun `a new import clears a previous error`() = runTest {
        val store = AppStore(parse = { p, src -> if (p == "broken.vcf") error("bad") else fakeContacts("a", source = src) },
            ioDispatcher = Dispatchers.Unconfined)
        store.importFile("broken.vcf", Source.APPLE)
        store.importFile("ok.vcf", Source.APPLE)
        assertNull(store.state.value.error)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreImportTest"`
Expected: At minimum the `FilePicker` reference in Step 3 doesn't exist yet; the import tests themselves exercise `AppStore.importFile` from Task 2. Write `FilePicker.kt` first (Step 3) — but run this test now and confirm it currently PASSES against Task 2's `importFile` (the import logic already exists). If it passes, that's expected; this task's new artifact is the `FilePicker` interface (Step 3) plus locking in the import behavior with these tests.

Note: This task adds the `FilePicker` seam used by the Import screen (Task 6) and pins import behavior with tests. If you prefer strict red-first, temporarily rename `importFile` to see the test fail, then restore — but do not commit the rename.

- [ ] **Step 3: Write `FilePicker.kt`**

```kotlin
package com.robsartin.contactotomy.ui

/** Opens a file chooser and returns the chosen path, or null if cancelled. */
fun interface FilePicker {
    fun pick(): String?
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreImportTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/FilePicker.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreImportTest.kt
git commit -m "feat(ui): add FilePicker seam and lock in namespaced import (#8)"
```

---

### Task 4: Remove imported file + totals helper

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreRemoveTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreRemoveTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreRemoveTest {
    private fun store() = AppStore(
        parse = { path, src ->
            val tag = if (src == Source.APPLE) "a" else "g"
            listOf(Contact(id = tag, source = src, name = ContactName(given = tag), rawVCard = ""))
        },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `removing a file drops its summary and its contributed contacts`() = runTest {
        val s = store()
        s.importFile("apple.vcf", Source.APPLE)   // imp0:a
        s.importFile("google.vcf", Source.GOOGLE) // imp1:g
        s.removeImportedFile("apple.vcf")
        assertEquals(listOf("google.vcf"), s.state.value.imported.map { it.path })
        assertEquals(listOf("imp1:g"), s.state.value.contacts.map { it.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreRemoveTest"`
Expected: FAIL — `removeImportedFile` unresolved.

- [ ] **Step 3: Add `removeImportedFile` to `AppStore`**

Track which contacts came from which file by remembering the per-file id prefix. Add a private map and update `importFile` to record it, then implement removal. Insert into `AppStore`:

```kotlin
    // add as a field
    private val prefixByPath = mutableMapOf<String, String>()
```
In `importFile`'s success branch, record the prefix before namespacing:
```kotlin
            val n = importCounter++
            val prefix = "imp$n:"
            prefixByPath[path] = prefix
            val namespaced = parsed.map { it.copy(id = prefix + it.id) }
```
Add the method:
```kotlin
    fun removeImportedFile(path: String) {
        val prefix = prefixByPath.remove(path) ?: return
        _state.update { st ->
            st.copy(
                imported = st.imported.filterNot { it.path == path },
                contacts = st.contacts.filterNot { it.id.startsWith(prefix) },
            )
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreRemoveTest"`
Expected: PASS. Also re-run the import test to confirm no regression: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreImportTest"` → PASS.

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreRemoveTest.kt
git commit -m "feat(ui): remove imported file drops its contacts (#8)"
```

---

### Task 5: Wizard shell composable

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/App.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppShellTest.kt`

- [ ] **Step 1: Write the failing UI test**

`src/test/kotlin/com/robsartin/contactotomy/ui/AppShellTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppShellTest {
    private fun storeWithOneContact(): AppStore {
        val store = AppStore(
            parse = { _, s -> listOf(Contact(id = "a", source = s, name = ContactName(given = "A"), rawVCard = "")) },
            ioDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { store.importFile("f.vcf", Source.APPLE) }
        return store
    }

    @Test
    fun `shell shows the four step labels`() = runComposeUiTest {
        setContent { App(AppStore(parse = { _, _ -> emptyList() })) }
        onNodeWithText("Import").assertExists()
        onNodeWithText("Merge").assertExists()
        onNodeWithText("Deletion").assertExists()
        onNodeWithText("Export").assertExists()
    }

    @Test
    fun `next advances to the merge stub when contacts exist`() = runComposeUiTest {
        setContent { App(storeWithOneContact()) }
        onNodeWithText("Next").performClick()
        onNodeWithText("Merge review — built in 4b").assertExists()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppShellTest"`
Expected: FAIL — `App` unresolved.

- [ ] **Step 3: Write `App.kt`**

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.collectAsState

@Composable
fun App(store: AppStore) {
    val state: AppState by store.state.collectAsState()
    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            StepIndicator(state.screen)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { store.back() }, enabled = state.screen != Screen.IMPORT) { Text("Back") }
                val nextEnabled = !(state.screen == Screen.IMPORT && state.contacts.isEmpty())
                Button(onClick = { store.next() }, enabled = nextEnabled) { Text("Next") }
            }
            when (state.screen) {
                Screen.IMPORT -> ImportScreen(store)
                Screen.MERGE -> Text("Merge review — built in 4b")
                Screen.DELETION -> Text("Deletion review — built in 4c")
                Screen.EXPORT -> Text("Export — built in 4d")
            }
        }
    }
}

@Composable
private fun StepIndicator(current: Screen) {
    val labels = listOf(Screen.IMPORT to "Import", Screen.MERGE to "Merge", Screen.DELETION to "Deletion", Screen.EXPORT to "Export")
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        labels.forEach { (screen, label) ->
            Text(if (screen == current) "[$label]" else label)
        }
    }
}
```

Note: `ImportScreen` is created in Task 6. For this task to compile, add a temporary minimal `ImportScreen` in `App.kt` ONLY IF Task 6 isn't done yet — but since tasks run in order, instead add a placeholder composable now and replace in Task 6. To keep tasks independent, define a minimal inline import body here:

Replace `Screen.IMPORT -> ImportScreen(store)` with `Screen.IMPORT -> Text("Import — choose files")` for THIS task, and switch it to `ImportScreen(store)` in Task 6. (The shell test only checks step labels + the Merge stub, so the import body is not asserted here.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppShellTest"`
Expected: PASS (both).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/App.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppShellTest.kt
git commit -m "feat(ui): add wizard shell with step indicator and stubs (#8)"
```

---

### Task 6: Import screen composable

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/ImportScreen.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/App.kt` (use `ImportScreen`)
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/ImportScreenTest.kt`

- [ ] **Step 1: Write the failing UI test**

`src/test/kotlin/com/robsartin/contactotomy/ui/ImportScreenTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ImportScreenTest {
    private fun store() = AppStore(
        parse = { _, s -> listOf(
            Contact(id = "a", source = s, name = ContactName(given = "A"), rawVCard = ""),
            Contact(id = "b", source = s, name = ContactName(given = "B"), rawVCard = ""),
        ) },
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `choosing an apple file shows its summary and total`() = runComposeUiTest {
        val store = store()
        setContent { ImportScreen(store, applePicker = FilePicker { "apple.vcf" }) }
        onNodeWithText("Choose Apple export").performClick()
        onNodeWithText("apple.vcf", substring = true).assertExists()
        onNodeWithText("2 contacts", substring = true).assertExists()
    }

    @Test
    fun `cancelling the picker imports nothing`() = runComposeUiTest {
        val store = store()
        setContent { ImportScreen(store, applePicker = FilePicker { null }) }
        onNodeWithText("Choose Apple export").performClick()
        onNodeWithText("0 contacts", substring = true).assertExists()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.ImportScreenTest"`
Expected: FAIL — `ImportScreen` unresolved.

- [ ] **Step 3: Write `ImportScreen.kt`**

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    store: AppStore,
    applePicker: FilePicker = NoopPicker,
    googlePicker: FilePicker = NoopPicker,
    otherPicker: FilePicker = NoopPicker,
) {
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        PickerRow("Choose Apple export") { applePicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.APPLE) } } }
        PickerRow("Choose Google export") { googlePicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.GOOGLE) } } }
        PickerRow("Add another file…") { otherPicker.pick()?.let { p -> scope.launch { store.importFile(p, Source.FILE) } } }

        if (state.importing) Text("Importing…", Modifier.padding(top = 8.dp))
        state.error?.let { Text("Error: $it", Modifier.padding(top = 8.dp)) }

        val total = state.contacts.size
        Text("$total contacts from ${state.imported.size} files", Modifier.padding(vertical = 8.dp))

        state.imported.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("${f.path}  [${f.source}]  ${f.count} contacts", Modifier.padding(end = 8.dp))
                Button(onClick = { store.removeImportedFile(f.path) }) { Text("Remove") }
            }
        }
    }
}

private val NoopPicker = FilePicker { null }

@Composable
private fun PickerRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Button(onClick = onClick) { Text(label) }
    }
}
```

- [ ] **Step 4: Wire it into `App.kt`**

In `App.kt`, change the import branch back to use the screen:
```kotlin
                Screen.IMPORT -> ImportScreen(store)
```
(remove the temporary `Text("Import — choose files")` from Task 5).

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.ImportScreenTest" --tests "com.robsartin.contactotomy.ui.AppShellTest"`
Expected: PASS (the import screen tests plus the still-green shell tests).

- [ ] **Step 6: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/ImportScreen.kt src/main/kotlin/com/robsartin/contactotomy/ui/App.kt src/test/kotlin/com/robsartin/contactotomy/ui/ImportScreenTest.kt
git commit -m "feat(ui): add import screen with per-source pickers (#8)"
```

---

### Task 7: Real file dialog + wire main + run-the-app verification

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/AwtFilePicker.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/Main.kt`

- [ ] **Step 1: Write `AwtFilePicker.kt` (excluded from coverage)**

```kotlin
package com.robsartin.contactotomy.ui

import java.awt.FileDialog
import java.awt.Frame

/** Real file chooser backed by the native AWT file dialog, filtered to .vcf. */
class AwtFilePicker(private val title: String) : FilePicker {
    override fun pick(): String? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.setFilenameFilter { _, name -> name.endsWith(".vcf", ignoreCase = true) }
        dialog.isVisible = true
        val dir = dialog.directory ?: return null
        val file = dialog.file ?: return null
        return dir + file
    }
}
```

- [ ] **Step 2: Wire `Main.kt` to the real app**

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val store = remember { AppStore() }
    Window(onCloseRequest = ::exitApplication, title = "Contactotomy") {
        App(store)
    }
}
```

To pass real pickers into the Import screen, change `App.kt`'s import branch to provide them:
```kotlin
                Screen.IMPORT -> ImportScreen(
                    store,
                    applePicker = AwtFilePicker("Choose Apple vCard export"),
                    googlePicker = AwtFilePicker("Choose Google vCard export"),
                    otherPicker = AwtFilePicker("Choose a vCard file"),
                )
```
(The UI tests still pass their own fake pickers / the default no-op, so they are unaffected.)

- [ ] **Step 3: Build and run the full gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL (coverage still ≥90/65; `AwtFilePicker` and `MainKt` are excluded).

- [ ] **Step 4: Run the app and verify manually**

Run: `./gradlew run`
Expected: a window titled "Contactotomy" opens showing the step indicator with `[Import]` active and the three picker buttons. Click "Choose Apple export", select a real `.vcf`, and confirm the file summary + total count appear and that "Next" becomes enabled and advances to "Merge review — built in 4b". Capture a screenshot for the PR. (If you have no `.vcf` handy, export one from Contacts or use `src/test/resources/fixtures/apple-sample.vcf`.)

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/AwtFilePicker.kt src/main/kotlin/com/robsartin/contactotomy/ui/Main.kt src/main/kotlin/com/robsartin/contactotomy/ui/App.kt
git commit -m "feat(ui): wire native file dialog and app entry point (#8)"
```

---

## Self-Review

**Spec coverage:**
- §2/§3 build (Compose plugins, deps, application entry, uiTestJUnit4, raised Kover bounds, MainKt/AwtFilePicker excludes, xvfb CI): Task 1. ✓
- §4 state + navigation (AppState/Screen/ImportedFile, AppStore StateFlow, next/back gating, goTo): Tasks 2. ✓
- §4 import (background parse via injected dispatcher, id namespacing, error path, importing flag): Tasks 2–3. ✓
- §4 removeImportedFile: Task 4. ✓
- §5 import screen (per-source pickers via FilePicker, summary list, totals, importing, error, gated Next): Tasks 5–6. ✓
- wizard shell + stubs: Task 5. ✓
- §6 testing (AppStore unit tests; Compose UI tests for shell + import; only MainKt/AwtFilePicker excluded; run-the-app verification): Tasks 2–7. ✓
- §7 scope (stubs for Merge/Deletion/Export; no real 4b–4d): shell stubs only. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. Version notes (Compose/Kover/Compose-UI-test API) and the Task 5→6 temporary-import-body swap are explicit instructions, not missing logic.

**Type consistency:** `AppStore(parse, ioDispatcher)` constructor, `state: StateFlow<AppState>`, `importFile(path, source)` (suspend), `removeImportedFile(path)`, `next/back/goTo` are consistent across Tasks 2–7 and all tests. `Screen` enum order (IMPORT, MERGE, DELETION, EXPORT) drives both navigation and the step indicator. `FilePicker.pick(): String?` matches the fake pickers in the UI tests and the `AwtFilePicker` impl. `ImportScreen(store, applePicker, googlePicker, otherPicker)` signature matches its call in `App.kt` and the tests.

**Risk notes for the executor:** (1) Exact Compose 1.7.x / Compose-UI-test API symbols (`runComposeUiTest`, `onNodeWithText`, `ExperimentalTestApi`) may vary slightly by version — adjust imports to the resolved version, keep behavior. (2) If headless UI tests fail locally on a CI-like environment, that's the xvfb case (Task 1 Step 3 handles CI). (3) Task 3's import tests already pass against Task 2's `importFile`; the task's new artifact is the `FilePicker` seam — don't force a fake red.

# App Shell + Import (Plan 4a) — Design Spec

Date: 2026-06-24
Status: Approved (design); pending implementation plan
Builds on: `2026-06-23-contactotomy-design.md` (§10 UI) and ADR-0002/0006.
Tracking issue: #8
Part of: Plan 4 (Compose UI), sub-plan 4a of 4 (4b merge review, 4c deletion review, 4d export + instructions).

## 1. Purpose

Stand up the Contactotomy Compose Desktop application: a wizard shell across the
four pipeline steps, a shared observable state holder, and a working **Import**
screen that loads Apple/Google vCard exports via the existing `VcfImporter`. This
is the foundation every later screen renders into. Merge/Deletion/Export are stubs
here, built in 4b–4d.

## 2. Key decisions

- **Plain, framework-free state holder** (`AppStore`) exposing an immutable
  `AppState` over a `MutableStateFlow`, plus intent functions. All logic lives
  here and is unit-tested; composables are thin renderers.
- **Wizard/stepper navigation** driven by a `Screen` enum in state (Back/Next),
  no navigation library. Next is gated (cannot leave IMPORT with zero contacts).
- **Per-source labeled pickers** on the Import screen (Apple / Google / Other),
  each tagging imported contacts with their `Source`.
- **Composables are tested with Compose UI tests** (`runComposeUiTest`, headless)
  and count toward coverage; only the app entry point (`MainKt`) and the real AWT
  file dialog are excluded. Coverage floors raised to **line ≥ 90%, branch ≥ 65%**
  (ADR-0012). Logic still also lives in testable holders.
- **Contact ids are namespaced per import** so accumulating multiple files keeps
  ids unique for the matcher.

## 3. Build & architecture

New package `com.robsartin.contactotomy.ui` (depends on `core`; Konsist forbids
the reverse, ADR-0006). Build additions:

- Plugins: `org.jetbrains.compose` (~1.7.x) and `org.jetbrains.kotlin.plugin.compose`
  (matching Kotlin 2.0.21). Exact versions pinned/verified in the plan.
- Dependency: `implementation(compose.desktop.currentOs)`; test dependency
  `compose.desktop.uiTestJUnit4` for headless Compose UI tests.
- `compose.desktop.application { mainClass = "com.robsartin.contactotomy.ui.MainKt" }`.
- Kover: raise floors to **line ≥ 90%, branch ≥ 65%** (ADR-0012); exclude only the
  app entry point (`MainKt`) and the real AWT `FileDialog` adapter. Composables are
  covered by Compose UI tests, not excluded. CI may need a virtual display
  (`xvfb-run`) for the Skiko offscreen renderer on Linux — decided in the plan.

Layering: `MainKt` → `App` composable (wizard shell) → screen composables, all
reading `AppState` and calling `AppStore` intents. The native file dialog is
isolated behind a `FilePicker` interface so screen/store logic is testable with a
fake; the real AWT `FileDialog` implementation is the thin, run-the-app-verified
part.

## 4. State & navigation

```kotlin
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

`AppStore` holds `MutableStateFlow<AppState>` (exposed as read-only `StateFlow`)
and provides intents:

- `importFile(path, source)` — parse the file via `VcfImporter(source)` on a
  background dispatcher; set `importing=true` while running; on success append the
  parsed (id-namespaced) contacts and an `ImportedFile` summary; on failure set
  `error` (no throw). Clear any prior `error` on a new attempt.
- `removeImportedFile(path)` — drop that file's summary and its contributed
  contacts.
- `next()` / `back()` — move along the `Screen` order; `next()` from IMPORT is a
  no-op when `contacts` is empty.
- `goTo(screen)`.

**id namespacing:** `importFile` prefixes each imported contact's id with a
per-import counter (e.g. `imp0:apple-3`) so accumulated contacts have unique ids.

**Navigation:** the shell shows a step indicator (Import · Merge · Deletion ·
Export) reflecting `state.screen`, with Back/Next; Next is disabled on IMPORT
until ≥1 contact is imported. Merge/Deletion/Export are placeholder panels in 4a.

**Threading:** imports run off the UI thread; the store is the single source of
truth. The store takes an injectable `CoroutineDispatcher` (default background) so
tests run deterministically.

## 5. Import screen

- **Per-source pickers:** labeled slots — *Apple export*, *Google export*, *Add
  another file…* (source `FILE`). "Choose…" opens the `FilePicker` (real impl: AWT
  `FileDialog` filtered to `.vcf`); selection calls `store.importFile(path,
  source)`.
- **File summary list:** name, source badge, contact count, and a Remove button
  per imported file.
- **Totals:** "N contacts from M files"; an importing indicator while a parse
  runs; inline `error` text on failure.
- **Next** enabled once ≥1 contact is imported.
- Empty state: a brief hint (the full export/import guide arrives in 4d).

## 6. Testing & verification

- **`AppStore` unit tests** (no Compose), the bulk of coverage: import appends +
  namespaces ids (fake `FilePicker`, real `VcfImporter` over a fixture vcf); remove
  drops the right contacts; next/back honor the gate; error path sets `error`
  without throwing; `importing` toggles around a parse (deterministic via injected
  dispatcher).
- **Pure helpers** (id-namespacing, totals) tested directly.
- **Compose UI tests** (`runComposeUiTest`, headless): render the wizard shell +
  Import screen with a fake `FilePicker` and a real/seeded `AppStore`; assert the
  step indicator, that choosing a file shows its summary + total count, that Next
  is disabled with zero contacts and enabled after import, that Remove clears a
  file, and that an import error renders inline. These cover the composables.
- **Excluded from coverage:** only `MainKt` and the real AWT `FileDialog` adapter.
- **Run-the-app verification** (`./gradlew run`): window opens on the wizard shell,
  pick a real `.vcf`, see counts + file list, Next advances to the stub Merge
  screen. Screenshot/notes in the PR.
- Konsist continues to enforce `core` has no UI/Compose imports.

## 7. Scope boundary

In scope: Compose build setup, `ui` package, `AppStore`/`AppState`, wizard shell,
Import screen, `FilePicker` abstraction, stubs for Merge/Deletion/Export. Out of
scope: the merge UI (4b), deletion UI (4c), export + instructions (4d).

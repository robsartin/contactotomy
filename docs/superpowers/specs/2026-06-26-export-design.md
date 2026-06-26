# Export + Instructions (Plan 4d) — Design Spec

Date: 2026-06-26
Status: Approved (design); pending implementation plan
Builds on: Plan 1 exporter (`VcfExporter`), Plan 4a (`AppStore`, wizard, `FilePicker`), Plan 4c (SAVE-mode `AwtFilePicker`), ADR-0003/0004 (file pipeline, mirror-into-both), ADR-0012/0013.
Tracking issue: #18
Part of: Plan 4 (Compose UI), sub-plan 4d of 4 — the MVP finish line.

## 1. Purpose

The wizard's final step: write the cleaned contacts to a single vCard 3.0 file and
render the export → backup → wipe → import guide for Apple and Google. Completes
the end-to-end pipeline: Import → Merge → Deletion → **Export**.

## 2. Decisions

- **One combined `contacts-clean.vcf`** (ADR-0004 mirror-into-both): export the
  most-processed set as a single vCard 3.0 file the user imports into both
  accounts. No per-source split.
- **Instructions as a bundled resource** (`export-instructions.md`), rendered
  in-app and referenced from the repo `README.md` — single source of truth.
- **Plain scrollable text** for the instructions (no markdown-rendering
  dependency — YAGNI).
- Logic in a small testable **`ExportStore`**; file I/O stays in the composable
  (store string-based), consistent with the deletion screen.

## 3. Data flow

The Export step works on the most-processed set via a pure helper:

```kotlin
fun workingContacts(state: AppState): List<Contact> =
    state.finalContacts ?: state.mergedContacts ?: state.contacts
```

(This is the same precedence the Deletion step already uses for its input.) App.kt
passes `workingContacts(state)` to the Export screen. The screen's `ExportStore`
generates the vCard via the existing `core.exporter.VcfExporter`; the composable
writes it to the path chosen via the SAVE-mode `FilePicker`. On commit there is no
further pipeline step (it is the last screen).

## 4. Store

Package `com.robsartin.contactotomy.ui`.

```kotlin
data class ExportState(
    val contactCount: Int,
    val exportedPath: String? = null,
    val error: String? = null,
)

class ExportStore(
    private val contacts: List<Contact>,
    private val exporter: VcfExporter = VcfExporter(),
) {
    // StateFlow<ExportState> seeded with contactCount = contacts.size
    fun vcard(): String = exporter.export(contacts)
    fun recordExported(path: String)   // sets exportedPath, clears error
    fun recordError(message: String)   // sets error
}
```

`vcard()` delegates to the already-tested `VcfExporter.export` (vCard 3.0). The
composable's Save flow: `FilePicker.pick()` → `File(path).writeText(store.vcard())`
→ `recordExported(path)`; on exception `recordError(e.message)`. The store never
touches the filesystem (testable with strings).

`workingContacts` lives next to `AppState` (a top-level function in the `ui`
package), unit-tested directly.

## 5. UI

Single-pane Export screen (wizard's last step; Back works, no Next):

- **Summary:** "Ready to export N cleaned contacts" (`contactCount`).
- **Export:** "Save cleaned vCard…" button → SAVE `FilePicker` (default name
  `contacts-clean.vcf`) → writes `store.vcard()`. After success: "✓ Exported N
  contacts to `<path>`"; on failure, the error message inline.
- **Instructions panel:** a scrollable rendering of the bundled
  `export-instructions.md` — the full export → backup → wipe → import guide for
  Apple + Google, including: how to export a vCard from each; save a backup copy
  first; delete existing contacts in each account before importing (to avoid
  duplicates); import the one file into both; and the documented tradeoffs
  (re-import resets `createdAt`, loses Apple group membership; Google labels
  survive via `CATEGORIES`).

Composables stay thin over the store; covered by `runComposeUiTest`. The native
Save dialog is the only excluded glue.

## 6. Instructions resource

`src/main/resources/export-instructions.md` holds the guide, loaded via the
classpath (like `nicknames.csv`). The repo `README.md` references it (or includes
its content) so the guide is the single source of truth. Content covers the Apple
and Google export/import steps and the ADR-0004 wipe-and-mirror workflow with its
tradeoffs.

## 7. Testing

- **`ExportStore` unit tests:** `vcard()` produces vCard-3.0 text containing a
  contact's data and round-trips via `VcfImporter`; `recordExported` /
  `recordError` update state; `contactCount` reflects input size.
- **`workingContacts` unit test:** picks `finalContacts` → else `mergedContacts` →
  else `contacts`, in that precedence.
- **Compose UI tests** (`runComposeUiTest`, fake `FilePicker` + `@TempDir`): the
  count renders; clicking Save writes the file (assert the file exists with vCard
  content) and shows the exported-path confirmation; the instructions text renders
  (assert a known line from the resource).
- Konsist keeps `core` UI-free; coverage floors line ≥90 / branch ≥65.

## 8. Scope

In scope: `workingContacts` helper, `ExportStore`, the Export screen, the bundled
`export-instructions.md` (+ README reference), wiring into the wizard. Out of
scope: 4b-2 manual merge; usage signals. After this, the full Import → Merge →
Deletion → Export pipeline runs end to end — the MVP.

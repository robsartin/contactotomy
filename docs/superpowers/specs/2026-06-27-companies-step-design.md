# Companies Review Step — Design Spec (#37)

Date: 2026-06-27
Status: Approved (design); pending implementation plan
Builds on: #29 (`CompanyNameDetector`), #36 (empty/company-only name), #30 (theme
components). Companion to #36 (Phase A was the merge flow; this is Phase B for
standalone cards).
Tracking issue: #37

## 1. Purpose

A company card that is **not** part of any merge cluster (e.g. a lone "Round Rock
ISD") has no place today to be turned into a clean company-only card (name → org,
name cleared). This adds a **reviewed** step — not a silent transform — because
the WEAK detector signal would mangle real single-name people ("Mom", "Dave") and
would still miss cards the heuristic doesn't catch. The user confirms each card.

## 2. Decisions

- **New wizard step `COMPANIES`, after Merge:** `IMPORT → MERGE → COMPANIES →
  DELETION → EXPORT`. Runs on the post-merge set, so it never disturbs the
  name-gated matcher; its output feeds Deletion.
- **List all post-merge contacts, suspects pre-checked.** Every contact is a row;
  the detector's **high-precision** suspects (LEGAL_SUFFIX / AMPERSAND / KEYWORD —
  **never WEAK**) start ticked. WEAK/undetected cards are listed but unticked, and
  the user can tick any of them (covers "Round Rock ISD"). A filter box navigates
  long lists.
- **Action on confirm:** name → org **only when org is blank**; if an org already
  exists, keep it; **always clear the name**. (User's choice — never overwrite a
  real org.)
- **No matcher/merger/exporter change.** New code: a pure `core` normalizer + a UI
  store + a screen + wizard wiring.

## 3. Core (`core/company/`)

- **`companyNameText(name: ContactName): String`** — `formatted` (if non-blank)
  else `given + family` joined. Extracted as the single source of truth and reused
  by `CompanyNameDetector` (replacing its private inline `display`) and the
  normalizer. (Pure refactor of existing detector logic — behavior unchanged.)
- **`CompanyNameDetector.isHighPrecision(name): Boolean`** — `detect(name)` is one
  of `LEGAL_SUFFIX, AMPERSAND, KEYWORD` (excludes `WEAK` and `null`). Drives
  pre-checking.
- **`CompanyNormalizer.markAsCompany(contact: Contact): Contact`** —
  - `org` blank → `contact.copy(org = companyNameText(contact.name), name = ContactName())`;
  - `org` present → `contact.copy(name = ContactName())` (keep org, clear name).

## 4. Store (`ui/CompanyReviewStore`)

```kotlin
class CompanyReviewStore(private val contacts: List<Contact>) {
    // markedIds initialized to ids where CompanyNameDetector.isHighPrecision(name)
    val state: StateFlow<CompanyReviewState>   // markedIds: Set<String>
    fun toggle(id: String)                     // add/remove from markedIds
    fun commit(): List<Contact> = contacts.map { if (it.id in markedIds) CompanyNormalizer.markAsCompany(it) else it }
}
```
`CompanyReviewState(markedIds: Set<String>)`. The filter query is transient UI
state in the composable, not store state (YAGNI).

## 5. Screen (`ui/CompanyScreen`)

- A header (`SectionHeader("Mark companies")`) + a filter `TextField`.
- A `LazyColumn` of rows for all contacts whose `companyNameText` is non-blank
  (a card with no name at all has nothing to mark), filtered by the query
  (case-insensitive over the name): each row = a `Checkbox` (checked = marked) with
  the row clickable to toggle, the name + `SourceBadge`, and — when marked — a
  muted "→ org: <companyNameText>" hint showing the result.
- A summary line: "K of N marked as companies".
- Uses the existing theme/components; controls call `store.toggle(id)`.

## 6. Wizard wiring

- `Screen` enum gains `COMPANIES` between `MERGE` and `DELETION`.
- `AppState` gains `companyContacts: List<Contact>? = null`; `setCompanyContacts`.
- `workingContacts = finalContacts ?: companyContacts ?: mergedContacts ?: contacts`.
- `App.kt`: hoist a `CompanyReviewStore(state.mergedContacts ?: state.contacts)`;
  the deletion store's source becomes `state.companyContacts ?: state.mergedContacts ?: state.contacts`.
  The top **Next** on `COMPANIES` does `setCompanyContacts(companyStore.commit()); next()`.
  `StepIndicator` and `next()`/`back()` already iterate `Screen.entries`, so they
  pick up the new step; add the "Companies" label.
- `nextEnabled` for `COMPANIES` is always true (marking zero is valid).

## 7. Testing

- **`CompanyNormalizer`** (core): blank-org → org set + name cleared; existing-org
  → org kept + name cleared; `companyNameText` formatted-vs-given+family.
- **`CompanyNameDetector.isHighPrecision`**: true for legal-suffix/ampersand/
  keyword, false for WEAK ("ACME"/"Dave") and ordinary names.
- **`CompanyReviewStore`**: high-precision suspects pre-marked, WEAK/undetected
  not; `toggle` adds/removes; `commit` normalizes only marked, leaves others;
  manually toggling a card the detector does NOT flag (use a plain two-token name
  like "Blue Bottle", `detect == null`) then `commit` normalizes it — proving
  manual marks work for missed cards.
- **`CompanyScreen`** (`runComposeUiTest`): rows render with suspects pre-checked;
  the filter narrows the list; toggling a row updates the store; the "→ org" hint
  shows on a marked row.
- **End-to-end** (`AppFlowTest`): a fixture lone company card flows
  Import→Merge→**Companies (pre-checked)**→Deletion→Export → exported `ORG:` with
  no `FN:`. **Existing `AppFlowTest`/`AppNavigationTest` cases gain one extra
  `Next`** (Merge→Companies→Deletion) — mechanical, no assertions weakened.
- `./gradlew check` green (line ≥90 / branch ≥70); Konsist keeps `core` UI-free.

## 8. Scope

In scope: `core/company/CompanyNormalizer` + the `companyNameText`/`isHighPrecision`
helpers; `ui/CompanyReviewStore`; `ui/CompanyScreen`; `AppState`/`AppStore`/`App.kt`
wiring (+ the `Screen.COMPANIES` value); updated navigation/flow tests. Out of
scope: the matcher/merger/exporter; #39 (starter-set curation, NEVER_CONTACTED).

## 9. Notes

- A card with no name at all is omitted from the list (nothing to mark).
- Marking is idempotent: re-running `commit()` on an already-normalized card
  (blank name) is a no-op-ish (org already set, name already empty).
- The step is skippable in effect (mark nothing → pass-through), so it never blocks
  the wizard.

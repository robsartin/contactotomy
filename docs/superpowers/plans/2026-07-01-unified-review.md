# Unified Review step (Merge + Tidy) — Implementation Plan (#78)

> **For agentic workers:** implement task-by-task with pure TDD. Steps use checkbox
> (`- [ ]`) syntax. Each task ends with `./gradlew check` green and a commit.

**Goal:** Collapse Merge + Tidy into one two-section **Review** step
(Import → Review → Deletion → Export), behavior-preserving. Reuse `MergeReviewStore`;
retire `TidyStore`/`TidyScreen`.

**Architecture:** New `ReviewStore` composes the existing `MergeReviewStore`
(Section 1: duplicates) with singleton-tidy state (Section 2: cards to clean); one
`commit()` chains them. New `ReviewScreen` renders both sections. `AppState` gains
`reviewedContacts` (replacing `mergedContacts`+`tidyContacts`).

**Tech Stack:** Kotlin 2.0.21, Compose Desktop 1.7.3, StateFlow, kotlin.test + Compose UI test.

## Global Constraints

- Pure TDD: failing test → confirm fail → minimal impl → confirm green → commit.
- ALL changes in `ui` except the (unchanged) reuse of `core` company transforms;
  `core` stays UI-free (Konsist). Do NOT rewrite `MergeReviewStore` (compose it).
- Coverage floors line ≥90 / branch ≥70 (never lower); cover new branches.
- Behavior-preserving: the ported `AppFlowTest` assertions (final counts, ORG/FN,
  no-reply deletion, email→name, company-only) must still pass — that is the safety net.
- Spotless/ktlint + Konsist pass (`./gradlew check`); `./gradlew spotlessApply` if needed.
- Commit messages end with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch `78-unified-review`. First commit: copy the design spec to
  `docs/superpowers/specs/2026-07-01-unified-review-design.md` and this plan to
  `docs/superpowers/plans/2026-07-01-unified-review.md`.
- Read the design spec in full before starting; it holds the exact behavioral
  decisions (esp. §4 the store, §5 the screen, the singleton definition, the split).

---

### Task 1: `ReviewStore` (compose merge + singleton tidy)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/ReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/ReviewStoreTest.kt`
- Reference (do not modify): `ui/MergeReviewStore.kt`, `ui/TidyStore.kt` (logic to fold in),
  `core/company/CompanyNormalizer.kt`, `core/company/CompanyNameDetector.kt`.

**Produces:** `class ReviewStore(contacts)` exposing the internal merge store (for
Section 1), Section-2 state (`cleanCandidates()`, `markedIds`, `toggleClean(id)`,
`actionFor(c)`), and `commit(): List<Contact>`.

- [ ] **Step 1: Failing tests.** Build a fixture list: a duplicate pair sharing a
  phone (same person), a lone "Acme Inc" company card (no org), a lone nameless card
  with an email, and a plain lone person with a name+phone. Assert:
  - `cleanCandidates()` = the lone company card + the nameless-email card only (the
    plain person is not suggested; the paired cards are grouped, not singletons).
  - both candidates are in `markedIds` by default; `toggleClean` flips one.
  - `commit()` with the merge pair accepted (drive the internal merge store) AND both
    clean cards marked → output collapses the pair into one, sets the company card's
    org (name cleared) and the nameless card's name from its email; the plain person
    is unchanged. (Assert sizes and the org/name results.)
  - `commit()` with a clean card unticked leaves that card unchanged.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement per design §4: internal `MergeReviewStore(contacts)`;
  singleton = id not a member of any HIGH cluster and not in any uncertain pair (derive
  grouped ids from the matcher result / the merge store's items); suggested =
  `isHighPrecision(name) || (companyNameText(name).isBlank() && org.isNullOrBlank() && emails.isNotEmpty())`;
  `commit()` = `mergeStore.commit()` then map the tidy transform over `markedIds`.
- [ ] **Step 4:** Run, confirm pass (and the whole `ui` package for regressions).
- [ ] **Step 5:** Commit.

### Task 2: `ReviewScreen` (two sections)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/ReviewScreen.kt`
- Modify (factor for reuse): `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
  — extract the list + detail composables so they can render inside Section 1 without
  duplication (keep their testTags).
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/ReviewScreenTest.kt`

**Consumes:** Task 1.

- [ ] **Step 1: Failing UI tests** (`runComposeUiTest`): both section headers
  ("Duplicates to merge", "Cards to clean") render; a merge item can be selected and
  `Accept merge` clicked; a clean row checkbox (`mark:<id>`) toggles and its
  `→ org:`/`→ name:` hint shows; empty-state text when a section is empty.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement `ReviewScreen(store: ReviewStore)` per design §5: Section 1
  reuses the extracted merge composables driven by `store`'s internal merge store;
  Section 2 renders the clean rows (checkbox + name + `SourceBadge` + hint) like the
  old `TidyScreen`. Stacked, each scrollable; section headers; empty states. Reuse
  existing testTags.
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 3: Rewire wizard, rename state, remove Tidy, update flows

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt` (Screen enum;
  `reviewedContacts` replaces `mergedContacts`+`tidyContacts`; `setReviewedContacts`;
  `workingContacts`).
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/App.kt` (hoist `ReviewStore`;
  REVIEW branch commits `setReviewedContacts(reviewStore.commit())`; remove MERGE/TIDY
  branches + merge/tidy hoists; deletion source `reviewedContacts ?: contacts`;
  `StepIndicator` labels Import·Review·Deletion·Export).
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/ExportScreen.kt` /
  `ExportSummary` — update `exportSummary` for the `reviewedContacts` field (per design §3).
- Delete: `ui/TidyScreen.kt`, `ui/TidyStore.kt` and their tests
  `TidyScreenTest.kt`, `TidyStoreTest.kt`.
- Modify tests: `AppFlowTest.kt` (all 9 flows → Import→Review→Deletion→Export, one
  fewer Next; merge + tidy actions on the single Review screen), `AppStoreTest` /
  navigation tests, `ExportSummaryTest` (field rename), any test referencing
  `Screen.MERGE`/`Screen.TIDY`/`mergedContacts`/`tidyContacts`.

- [ ] **Step 1:** Update the failing tests first (adjust `AppFlowTest` flows and
  nav/summary tests to the new structure) — run to confirm they fail against the old wiring.
- [ ] **Step 2:** Rewire `AppStore`, `App.kt`, `ExportSummary`; delete Tidy files.
- [ ] **Step 3:** Run the full suite; fix until green. Confirm the preserved
  assertions (final counts, ORG/FN on export, no-reply deletion, email→name via
  Review Section 2, company-only) still hold.
- [ ] **Step 4:** `./gradlew check` fully green (coverage floors held; Konsist green;
  no dangling references to MERGE/TIDY/mergedContacts/tidyContacts).
- [ ] **Step 5:** Commit. Open PR to `main` with `Closes #78`.

## Self-review checklist (before PR)
- Wizard is Import→Review→Deletion→Export; no `Screen.MERGE`/`TIDY` remain.
- `ReviewStore.commit()` applies merges AND singleton tidies; behavior matches old
  Merge→Tidy for the AppFlowTest fixtures.
- `MergeReviewStore` unchanged (composed, not rewritten); its tests still pass.
- `TidyStore`/`TidyScreen` and their tests removed; no references remain.
- Coverage floors held; Konsist green; Spotless clean.

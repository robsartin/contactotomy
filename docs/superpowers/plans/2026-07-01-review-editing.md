# Review: full card editing (#79) — Implementation Plan

> **For agentic workers:** implement task-by-task with pure TDD. Steps use checkbox
> (`- [ ]`) syntax. Each task ends with `./gradlew check` green and a commit.

**Goal:** Editable name components / org / notes + freeform-add phone/email on the
merged-result card in Review Section 1, layered over the existing pick/exclude model.

**Architecture:** New per-`ReviewItem` override fields in `MergeReviewStore` applied
last in `commit()`; editable fields in the `MergeScreen` detail pane (reused by
`ReviewScreen` Section 1). No `core` change.

**Tech Stack:** Kotlin 2.0.21, Compose Desktop 1.7.3, StateFlow, kotlin.test + Compose UI test.

## Global Constraints
- Pure TDD; additive diff — do NOT disturb existing pick/exclude/conflict logic.
- All in `ui`; `core` untouched (Konsist). Coverage floors line ≥90 / branch ≥70.
- An item with no overrides must commit identically to today (regression tests are the safety net).
- Spotless/ktlint + Konsist green (`./gradlew check`).
- Commit messages end with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch `79-review-editing`. First commit: copy design spec →
  `docs/superpowers/specs/2026-07-01-review-editing-design.md` and this plan →
  `docs/superpowers/plans/2026-07-01-review-editing.md`. Read the spec fully first
  (§3 model, §4 commit precedence, §5 UI).

---

### Task 1: Store overrides + commit
**Files:** modify `ui/MergeReviewStore.kt`; test `ui/MergeReviewStoreTest.kt` (add cases).

**Produces:** `ReviewItem` fields `nameOverride: ContactName?`, `orgOverride: String?`,
`notesOverride: String?`, `addedPhones: List<String>`, `addedEmails: List<String>`;
intents `setNameComponent(itemId, NameComponent, String)` (assembles `nameOverride`,
seeded from the current effective name), `setOrgOverride`, `setNotesOverride`,
`appendSourceNotes`, `addPhone`/`removeAddedPhone`, `addEmail`/`removeAddedEmail`;
overrides applied last in `commit()` per spec §4.

- [ ] **Step 1: Failing tests** — `setNameComponent` builds a `nameOverride` such that
  `commit()` produces that name even when `nameChoiceId` was set; `setOrgOverride("X")`
  → merged org "X"; `setOrgOverride("")` → null org; `setNotesOverride`;
  `appendSourceNotes` = newline-join of members' non-blank notes (deduped);
  `addPhone`/`addEmail` appear in committed card (deduped); remove drops them; an item
  with NO overrides commits byte-identically to before (pick a representative existing
  merge scenario and assert unchanged).
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement. Add an `enum NameComponent { PREFIX, GIVEN, MIDDLE, FAMILY, SUFFIX }`.
  `setNameComponent` reads the item's current effective name (override if present else
  the value commit would produce) and copies the one component. Apply overrides at the
  end of the existing merged-contact construction in `commit()`.
- [ ] **Step 4:** Run, confirm pass (whole `ui` package).
- [ ] **Step 5:** Commit.

### Task 2: Editable name / org / notes UI
**Files:** modify `ui/MergeScreen.kt` (detail pane); test `ui/MergeScreenTest.kt` and/or
`ui/ReviewScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — for a selected merged item: the five name-component
  fields render pre-filled with the effective name (testTags `name-prefix`…`name-suffix`);
  editing `name-given` updates state and `commit()` reflects it; `org-edit` pre-filled,
  editing updates; `notes-edit` pre-filled; `append-notes` button fills notes from
  sources.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement per spec §5: add the editable block to the merge-result
  detail content; pre-fill from effective values; wire `onValueChange` → intents; keep
  existing name/org choice controls intact. Match theme/`Dimens` style.
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 3: Freeform add phone/email + gate
**Files:** modify `ui/MergeScreen.kt`; test `ui/MergeScreenTest.kt`/`ReviewScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — an add-phone input + button (`add-phone-input`/
  `add-phone-btn`) appends a typed phone shown as a removable chip; same for email
  (`add-email-input`/`add-email-btn`); the added value survives `commit()`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement the add-phone/add-email inputs + removable chips (reuse the
  existing pill style if handy).
- [ ] **Step 4:** `./gradlew check` fully green.
- [ ] **Step 5:** Commit. Open PR to `main` with `Closes #79`.

## Self-review checklist
- Overrides applied last; no-override items unchanged (regression tests pass).
- All new interactive fields have stable testTags.
- `core` untouched; coverage floors held; Konsist/Spotless green.

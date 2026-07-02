# Review: keyboard accelerators + carried cleanup (#82) — Implementation Plan

> Pure TDD, checkbox steps, each task ends `./gradlew check` green + commit.

**Goal:** Keyboard triage for Review Section 1 + a visible legend; plus the carried-over
#81 cleanup (previewContact reuses `DecisionApplier.adjust`).

## Global Constraints
- Pure TDD; additive. Coverage floors line ≥90 / branch ≥70 (never lower).
- Task 0 keeps `core` UI-free (Konsist); `ui`→`core` reuse is fine. Spotless/ktlint + Konsist green.
- Guard: key accelerators must NOT fire while a text edit field is focused.
- Commit messages end `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch `82-review-keyboard`. First commit: copy spec →
  `docs/superpowers/specs/2026-07-01-review-keyboard-design.md` and this plan →
  `docs/superpowers/plans/2026-07-01-review-keyboard.md`. Read the spec first.

---

### Task 0: Cleanup — previewContact reuses DecisionApplier.adjust
**Files:** modify `core/apply/DecisionApplier.kt`, `ui/MergeReviewStore.kt`; tests: existing.

- [ ] **Step 1:** Confirm the existing `MergeReviewStoreTest` includes a case asserting
  `previewContact(item)` matches that item's contact in `commit()` output for an item
  with an excluded phone (add one if missing); run — it should pass now (safety net).
- [ ] **Step 2:** Make `DecisionApplier.adjust(proposal, decision)` public; in
  `previewContact` step 1, build a `MergeDecision` from the item and call
  `DecisionApplier().adjust(...)`; delete the store's private `setSingle`/`removeValue`
  (+ unused `PhoneNormalizer` import).
- [ ] **Step 3:** `./gradlew check` green; grep confirms `setSingle`/`removeValue` exist
  only in `DecisionApplier`.
- [ ] **Step 4:** Commit (`refactor: previewContact reuses DecisionApplier.adjust`).

### Task 1: Keyboard handling for Section 1
**Files:** modify `ui/MergeScreen.kt` (and/or `ui/ReviewScreen.kt`); test
`ui/ReviewScreenTest.kt` / `ui/MergeScreen*Test.kt`.

- [ ] **Step 1: Failing UI tests** (`performKeyInput` / key events) — with ≥2 merge
  items: `DirectionDown` (or `J`) moves selection to the next item; `A` accepts the
  selected item (commit merges it); `R` rejects (keeps separate); `D` discards (member
  ids absent from commit); a key while an `OutlinedTextField` (edit field) is focused
  does NOT trigger the accelerator.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement per spec §3: a focusable Section-1 container with
  `Modifier.onPreviewKeyEvent` mapping the keymap (spec §2) to store intents
  (`accept`/`reject`/`discardItem`) and prev/next selection over the ordered item list;
  pass through when a text field is focused; request focus on appear. Case-insensitive.
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 2: Legend + gate
**Files:** modify `ui/MergeScreen.kt`/`ui/ReviewScreen.kt`; test as above.

- [ ] **Step 1: Failing test** — a `shortcut-legend` node renders under the Section-1
  header with the key hints.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Add the compact legend (`↑/↓ move · A accept · R keep separate · D
  discard`, testTag `shortcut-legend`, `appColors.muted`, small font).
- [ ] **Step 4:** `./gradlew check` fully green.
- [ ] **Step 5:** Commit. Open PR `Closes #82`.

## Self-review
- Task 0: single definition of field logic (core); store dup gone.
- Accelerators act on the selected item + move selection; text-field focus is respected.
- Legend visible. Coverage floors held; Konsist/Spotless green.

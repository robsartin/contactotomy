# Review: preview + discard + scrollbars (#81) — Implementation Plan

> Pure TDD, checkbox steps, each task ends `./gradlew check` green + commit.

**Goal:** (A) distinctive merged-result preview card driven by a single-source-of-truth
`previewContact`; (B) discard-cluster-entirely; (C) Review scrollbars.

## Global Constraints
- Pure TDD; additive. `previewContact` is the ONE definition of a committed item's
  result — `commit()` must use it (no drift). Coverage floors line ≥90 / branch ≥70.
- `core` untouched (Konsist); all `ui`. Spotless/ktlint + Konsist green.
- Commit messages end `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch `81-review-preview-discard`. First commit: copy spec →
  `docs/superpowers/specs/2026-07-01-review-preview-discard-design.md` and this plan →
  `docs/superpowers/plans/2026-07-01-review-preview-discard.md`. Read the spec first.

---

### Task 1: `previewContact` (single source of truth) + refactor commit
**Files:** modify `ui/MergeReviewStore.kt`; test `ui/MergeReviewStoreTest.kt`.

- [ ] **Step 1: Failing tests** — `previewContact(item)` returns the exact `Contact`
  `commit()` produces for that accepted item (test with a field exclusion + a name
  override + an org override; assert `previewContact(item)` == the item's contact in
  `commit()`'s output). A no-override item's preview equals `proposal.merged` shaped by
  decisions.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Extract the per-accepted-item result construction from `commit()` into
  `fun previewContact(item: ReviewItem): Contact` (applying exclusions/conflict
  choices/effective name-org-notes/override layer), and have `commit()` call it for each
  accepted item. Behavior identical (existing tests are the safety net).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 2: discard-cluster-entirely
**Files:** modify `ui/MergeReviewStore.kt`; test `ui/MergeReviewStoreTest.kt`.

- [ ] **Step 1: Failing tests** — `discardItem(itemId)` records the item's member ids in
  `discardedIds`; `commit()` omits those contacts entirely (a discarded 2-card cluster →
  both ids absent from output, size drops by 2 vs reject which keeps both). Discard ≠
  reject ≠ accept. Untouched flow commits identically (regression).
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Add `discardedIds: Set<String>` to state + `discardItem`; in `commit()`
  filter out contacts whose id ∈ `discardedIds` from the final output; mark the item
  resolved (new `Decision.DISCARD` or remove from pending — keep it consistent with how
  accept/reject are handled).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 3: UI — result card + discard button + scrollbars + gate
**Files:** modify `ui/MergeScreen.kt`, `ui/ReviewScreen.kt`; tests
`ui/MergeScreen*Test.kt` / `ui/ReviewScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — a `merged-result-card` renders the effective
  fields of the selected item and reflects an edit/exclusion; a `discard-cluster` button
  removes the cluster from committed output; scrollbars present on the Review lists
  (assert by testTag or a scroll action).
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement per spec §2/§3/§4: the accented "Merged result" `Card`
  (testTag `merged-result-card`) showing `store.previewContact(item)` (omit blank
  fields); the "Discard all" footer button (testTag `discard-cluster`) calling
  `store.discardItem(item.id)`; `VerticalScrollbar` + `rememberScrollbarAdapter` on the
  Review Section 1 list, Section 2 list, and detail pane. Match theme (`appColors`,
  `Dimens`).
- [ ] **Step 4:** `./gradlew check` fully green.
- [ ] **Step 5:** Commit. Open PR `Closes #81`.

## Self-review
- `previewContact` is the sole definition of a committed item's result; `commit()` uses
  it. Discard drops members from output; distinct from reject. Scrollbars on Review
  lists. Coverage floors held; Konsist/Spotless green.

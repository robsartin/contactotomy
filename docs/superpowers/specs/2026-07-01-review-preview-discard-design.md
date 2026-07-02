# Review: distinctive result preview, navigation & discard-cluster — Design Spec (#81)

Date: 2026-07-01
Status: Approved (autonomous)
Tracking issue: #81 (part of the unified Review step #78)

## 1. Purpose

Three Review improvements: (A) a **distinctive, clearly-visible "new merged card"**
preview showing exactly what commit will produce; (B) **discard a cluster entirely**
so none of its cards reach the output; (C) **visible scrollbars** on the Review lists.

## 2. A — distinctive result-preview card (single source of truth)

- Add `MergeReviewStore.previewContact(item: ReviewItem): Contact` — the exact contact
  that `commit()` would produce for this one accepted item (apply the item's field
  exclusions/conflict choices/name-org-notes overrides on top of `proposal.merged`,
  reusing the existing `effectiveName`/`effectiveOrg`/`effectiveNotes` and the override
  layer). **Refactor `commit()` to build each accepted item's result via
  `previewContact(item)`** so the preview and commit provably can't drift.
- In the merge detail pane (`MergeScreen`, reused by Review Section 1), render a
  visually **distinct result card** at the top of the pane — a `Card` with an accent
  (e.g. `appColors.accept` border/background tint from the theme) titled "Merged result",
  showing `previewContact(item)`'s name, org, phones, emails, addresses, urls, title,
  notes, categories (omit blanks). testTag `merged-result-card`. This is the "new card
  you'll get," clearly separated from the source cards and the edit controls.

## 3. B — discard a cluster entirely

Distinct from "Keep separate" (reject → members pass through unmerged) and the existing
delete-from-review (removes the suggestion): **discard drops all member cards from the
output.**

- Add `discardedIds: Set<String>` to `MergeReviewState` and `discardItem(itemId)` which
  adds the item's cluster member ids to `discardedIds` (and marks the item resolved so
  it leaves the pending queue — e.g. set its decision to a new `Decision.DISCARD`, or
  filter it out; pick the cleaner of the two and keep it consistent).
- `commit()`: exclude any contact whose id is in `discardedIds` from the final output
  (they are neither merged nor passed through). Everything else unchanged.
- UI: a **"Discard all"** button in the detail footer (testTag `discard-cluster`),
  visually distinct from Accept / Keep separate, with a short caption ("removes these
  from the export"). Keep the existing actions.

## 4. C — visible scrollbars in Review

Add Compose Desktop `VerticalScrollbar` + `rememberScrollbarAdapter` to the Review
screen's scrollable areas — Section 1 list, Section 2 list, and the detail pane —
mirroring what #83 did for Deletion. (App-wide theming/polish is still #84; this is the
Review-specific navigation the user asked for.)

## 5. Testing

- **`MergeReviewStore`**: `previewContact(item)` equals what `commit()` produces for that
  item (drive an item with an exclusion + a name/org override; assert the preview
  matches the committed contact); `discardItem` puts member ids in `discardedIds` and
  `commit()` omits them from the output (a discarded 2-card cluster → those 2 ids absent;
  count drops accordingly); discard vs reject differ (reject keeps 2 separate, discard
  keeps 0); an un-touched flow still commits identically (regression).
- **`MergeScreen`/`ReviewScreen` UI**: the `merged-result-card` renders the effective
  fields for a selected item and updates when an edit/exclusion changes; the
  `discard-cluster` button removes the cluster from the committed output; scrollbars
  present (assert via testTag or that long lists scroll).
- `./gradlew check` green: line ≥90 / branch ≥70 (cover new branches), Spotless/ktlint,
  Konsist (no `core` change).

## 6. Scope / notes

- No `core` change; all `ui`.
- `previewContact` is the single source of truth shared by the preview and `commit()`
  (same anti-drift discipline as #79's effective-value helpers).
- YAGNI: no per-field diff highlighting in the result card (just the clean result);
  discard applies to a whole cluster (not per-member).

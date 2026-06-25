# 13. Merge-review state in a dedicated store

Date: 2026-06-24

## Status

Accepted

## Context

The merge-review screen (Plan 4b) is the most stateful UI in the app: a list of
proposals from three origins (high-confidence clusters, uncertain pairs, manual
merges), each with an accept/reject/skip decision plus per-field exclusions and
conflict choices, bulk operations, a manual-merge selection mode, and a commit
step that produces the working contact set. ADR-0012 requires UI logic to live in
testable, framework-free holders so it counts toward coverage and the composables
stay thin. Folding all of this into `AppStore` would overload it; the merge review
is a self-contained interaction with its own lifecycle (built on entry, discarded
or committed on exit).

## Decision

Introduce a dedicated **`MergeReviewStore`** (in `com.robsartin.contactotomy.ui`,
depending only on `core`) that owns merge-review state:

- Constructed from the imported contacts with `ContactMatcher`/`ContactMerger`
  injected; builds the initial `ReviewItem`s (high → accept, uncertain → reject).
- Exposes a `StateFlow<MergeReviewState>` and pure intent functions
  (setDecision, toggleField, chooseConflict, acceptAllHighConfidence, manualMerge,
  removeManual, commit).
- `commit()` delegates the actual merging to the already-tested
  `core.apply.applyDecisions`; the store only assembles `MergeDecision`s and
  enforces UI-level rules (defaults, the consumed-card guard, the deterministic
  double-touch downgrade).
- On commit it hands the merged `List<Contact>` back to `AppStore`
  (`mergedContacts`), keeping `AppStore` the single source of the pipeline's
  working set while delegating the merge interaction here.

## Consequences

- Merge-review logic is unit-tested in isolation (no Compose), satisfying the
  coverage floors; composables render `MergeReviewState` and call intents.
- `AppStore` stays focused on pipeline/navigation; the merge screen's complexity is
  encapsulated and disposable.
- The store reuses the `core` engine rather than reimplementing merge logic; UI
  rules (decision defaults, guards, downgrade) are the only new behavior and are
  the testable surface.
- The same pattern (a per-screen store delegating to `core`) is the template for
  the deletion screen (4c).

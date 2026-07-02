# Review: keyboard accelerators (+ deferred cleanup) — Design Spec (#82)

Date: 2026-07-01
Status: Approved (autonomous)
Tracking issue: #82 (part of the unified Review step #78)

## 0. Carried-over cleanup (do first)

A #81 cleanup didn't make it into the merged PR: `MergeReviewStore.previewContact`
still uses private `setSingle`/`removeValue` copies that duplicate (and diverge from —
missing the `rawPhones` branch) `core/apply/DecisionApplier`'s field logic. Fix as
Task 0 here:
- Make `DecisionApplier.adjust(proposal: MergeProposal, decision: MergeDecision): Contact`
  **public** (currently private); keep its `setSingle`/`removeValue` as the single
  definition.
- In `MergeReviewStore.previewContact` step 1, build
  `MergeDecision(clusterId = item.proposal.cluster.id, action = Action.ACCEPT,
  excludedValues = item.excludedValues, conflictChoices = item.conflictChoices)` and
  call `DecisionApplier().adjust(item.proposal, decision)` for the base contact; keep
  steps 2–4 (effective name/org/notes, override layer, added phones/emails, title
  clearing) unchanged.
- Delete the private `setSingle`/`removeValue` from `MergeReviewStore`.
- Behavior-preserving (previewContact already governs commit output); existing tests
  (incl. `previewContact == commit output`) are the safety net.

## 1. Purpose

Fast keyboard triage of the Review step's **Section 1 (Duplicates to merge)** so the
user isn't mouse-bound, plus a visible legend so the keys are discoverable.

## 2. Keymap (Section 1 triage)

Acts on the currently selected merge item; moves selection through the Section-1 items:

| Key | Action |
|-----|--------|
| `↓` / `J` | select next item |
| `↑` / `K` | select previous item |
| `A` / `Enter` | Accept selected (merge) |
| `R` | Reject selected (keep separate) |
| `D` | Discard selected (drop from output) |

Keys are case-insensitive; ignore when a text field (edit fields) has focus so typing
into name/org/notes isn't hijacked.

## 3. Implementation

- Add key handling to the Section-1 area (in `MergeScreen`, which Review Section 1
  reuses): a focusable container with `Modifier.onPreviewKeyEvent` that maps the keymap
  to the existing store intents (`accept`/`reject`/`discardItem`) and to selection
  moves over the current ordered item list. Reuse the existing selected-item state;
  moving selection = pick the prev/next id in the displayed list.
- Guard: when focus is in an `OutlinedTextField` (the edit fields), let the event pass
  through (return `false` from the handler) so typing works normally.
- Request focus on the Section-1 container when the Review screen appears so keys work
  without a click.

## 4. Legend (discoverability)

A compact, always-visible shortcut legend under the "Duplicates to merge" header, e.g.
`↑/↓ move · A accept · R keep separate · D discard` (testTag `shortcut-legend`). Style
with `appColors.muted` / small font.

## 5. Testing

- **Cleanup (Task 0):** existing tests stay green; add/confirm `previewContact(item)`
  equals that item's contact in `commit()` output (incl. an excluded phone → rawPhones
  handling now applied via core).
- **Keyboard (Compose UI test, `performKeyInput`/key events):** with a Review screen
  showing ≥2 merge items, `↓` moves selection; `A` accepts the selected item (its
  decision becomes ACCEPT / it commits merged); `R` rejects; `D` discards (member ids
  absent from commit); a key press while an edit `OutlinedTextField` is focused does NOT
  trigger the accelerator (types normally instead).
- **Legend:** the `shortcut-legend` renders.
- `./gradlew check` green: line ≥90 / branch ≥70 (cover new branches), Spotless/ktlint,
  Konsist (Task 0 keeps core UI-free; the store→core reuse is `ui`→`core`, allowed).

## 6. Scope / notes

- Keyboard targets **Section 1** triage (the click-heavy part). Section-2 clean-mark
  toggling stays mouse-driven (YAGNI).
- No new persisted config; keymap is fixed (a user-configurable keymap is out of scope).

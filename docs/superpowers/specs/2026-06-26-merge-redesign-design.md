# Merge Screen Redesign — Design Spec

Date: 2026-06-26
Status: Approved (design); pending implementation plan
Builds on: Plan 4b-1 merge review (`MergeReviewStore`, `MergeScreen`), ADR-0012/0013. core engine (Plan 2) unchanged.
Tracking issue: #26

## 1. Purpose

The merge screen works correctly but gives poor feedback and weak controls
(user-reported): accepted/rejected clusters never leave the list, the detail pane
doesn't clearly identify which source card is which, and field/value selection
uses clunky unicode-in-buttons. This redesign keeps the matching/merging engine
untouched and reworks the review **state model** and the **screen** so the user
actively confirms each cluster with clear progress and real controls.

Visual mockup approved (Needs-review → Resolved list; distinct source cards;
checkboxes for multi-value fields, radios for single-value/conflict + name).

## 2. Decisions

- **Everything starts undecided.** Add `Decision.PENDING`; every cluster starts
  `PENDING` (drop the old HIGH→ACCEPT / UNCERTAIN→REJECT auto-defaults). The user
  actively confirms each.
- **Drop `SKIP`.** `PENDING` is the "not yet decided" state; each cluster is
  **Accept merge** or **Keep separate**.
- **Decide → move out of the list.** `ACCEPT`/`REJECT` move a cluster from
  "Needs review" to "Resolved"; **Undo** returns it to `PENDING`.
- **Commit allowed with pending clusters** — they pass through unmerged, with a
  visible "N still pending" note (no silent skip).
- **Name choice lives in the store**, not core: `commit()` applies the chosen
  card's name after the tested `applyDecisions` runs. Multi-value checkboxes and
  org/title/notes conflict radios use the engine's existing `excludedValues` /
  `conflictChoices`.
- **core matcher/merger/apply engine is unchanged.**

## 3. State model (`MergeReviewTypes` + `MergeReviewStore`)

```kotlin
enum class Decision { PENDING, ACCEPT, REJECT }   // SKIP removed; PENDING added

data class ReviewItem(
    val id: String,
    val origin: Origin,                 // HIGH / UNCERTAIN (MANUAL reserved)
    val proposal: MergeProposal,
    val decision: Decision = Decision.PENDING,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
    val nameChoiceId: String? = null,   // member contact id whose name to use; null = engine default
)
```

`MergeReviewState` keeps `items` + `committed`. Derived (computed in the store or
screen): `pending = items.filter { it.decision == PENDING }`, `resolved = the
rest`, `willMerge = count ACCEPT`, `pendingCount = count PENDING`.

`MergeReviewStore` intents:
- `accept(itemId)` / `reject(itemId)` — set the decision.
- `undo(itemId)` — set decision back to `PENDING`.
- `acceptAllHighConfidence()` — set every `HIGH` item to `ACCEPT` (shortcut).
- `toggleField(itemId, ExcludedValue)`, `chooseConflict(itemId, field, value)` —
  unchanged.
- `chooseName(itemId, memberId)` — set `nameChoiceId`.
- `commit()` — `applyDecisions(contacts, acceptedProposals, decisions)` as today
  (only `ACCEPT` items), then **post-process**: for each accepted item with a
  `nameChoiceId`, replace the corresponding merged contact's `name` with that
  member's `name`. (Match the merged contact by its deterministic merged id.)
  Returns the final list; sets `committed = true`. The double-touch downgrade from
  4b-1 is retained.

Initial build: HIGH clusters and UNCERTAIN pairs both become `ReviewItem`s with
`decision = PENDING` (no auto-accept/reject).

## 4. Screen (`MergeScreen`)

**Left — review list, two sections:**
- **Needs review (N):** the `PENDING` items as selectable rows — name (or
  "A ↔ B" + reason for uncertain), card count, a confidence tag (HIGH / maybe). A
  progress bar + "X of Y reviewed", and an **Accept all high-confidence** button.
- **Resolved (M) ▾:** collapsible; ACCEPT (✓ will merge) and REJECT (✕ kept
  separate) rows, each with **Undo**.

**Right — detail pane (real Compose controls):**
- **Source cards:** each cluster member as a distinct bordered card with a header —
  source badge (Apple/Google/Other), ★primary on the newest (by `modifiedAt`), the
  modified date — then name, phones, emails, org, labels.
- **Merged result — "tick what to keep":**
  - Single-value/conflict fields (name, org, title, notes) → **`RadioButton`
    group** (pick one; newest/most-complete pre-selected). Name radio sets
    `chooseName`; org/title/notes radios set `chooseConflict`.
  - Multi-value fields (phones, emails, addresses, urls, labels) → **`Checkbox`
    per value** (include/exclude via `toggleField`), each tagged with its source.
- **Decision buttons:** **✓ Accept merge** / **✕ Keep separate** → move the
  cluster to Resolved and advance selection to the next pending item.

**Bottom:** "Will merge K clusters · N still pending". The wizard **Next** commits
(unified navigation, #24).

Composables stay thin over `MergeReviewStore`. Existing `MergeScreen` tests are
updated to the new structure/controls.

## 5. Testing

- **`MergeReviewStore`:** clusters start PENDING; accept/reject set decision; undo
  → PENDING; pendingCount/resolvedCount; acceptAllHighConfidence; commit merges
  only ACCEPT and applies the name override; toggleField/chooseConflict still
  drive exclusions/conflicts; double-touch downgrade retained.
- **Compose UI** (`runComposeUiTest`): Needs-review vs Resolved render with counts;
  deciding moves a row to Resolved and advances selection; Undo returns it; a real
  `Checkbox` toggles a field value (assert store + merged result); a `RadioButton`
  picks a conflict/name value; source cards show source badge + ★primary + date;
  the "N still pending" note shows.
- Konsist keeps `core` UI-free; floors line ≥90 / branch ≥70.

## 6. Scope

In scope: `MergeReviewTypes`, `MergeReviewStore`, `MergeScreen` (and their tests).
The `core` matcher/merger/apply engine is unchanged (name override lives in the
store). Out of scope: 4b-2 manual merge; deletion/export screens.

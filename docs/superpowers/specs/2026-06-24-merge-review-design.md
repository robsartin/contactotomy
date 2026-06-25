# Merge Review (Plan 4b) — Design Spec

Date: 2026-06-24
Status: Approved (design); pending implementation plans
Builds on: `2026-06-23-contactotomy-design.md` (§10), Plan 2 engine (ADR-0005/0008), Plan 4a (`AppStore`), ADR-0012.
Tracking issues: #10 (4b-1). 4b-2 gets its own issue.

## 1. Purpose

The merge-review screen — the visual centerpiece. On entering the Merge step it
runs the existing matcher/merger over the imported contacts, lets the user review
each proposed merge (primary + stacked secondaries → merged card with per-field
toggles and conflict switches), bulk-accept the high-confidence ones, manually
merge cards the matcher missed, and commit the result as the working contact set
for the deletion step.

Chosen UX (from visual brainstorming): **layout B — list + detail**, with the
**merged-card-with-inline-toggles** detail.

## 2. Decisions

- **Uncertain pairs are included**, in a separate "Possible matches" section,
  defaulting to reject (opt-in).
- **Bulk "Accept all high-confidence"** is provided (with review/undo before
  commit).
- **Manual merge is in scope** (force-merge cards the matcher missed), built in a
  second PR.
- **Logic in a testable `MergeReviewStore`** (ADR-0012/0013); composables thin and
  UI-tested.
- **Decomposition: one spec, two PRs** — 4b-1 (review proposed clusters +
  uncertain + bulk + commit) and 4b-2 (manual merge layered on).

## 3. Data flow

On advancing to Merge, 4b runs `core.matcher.ContactMatcher.match(AppStore.contacts)`
→ HIGH clusters + uncertain pairs, then `core.merger.ContactMerger.merge()` per
cluster → `MergeProposal`s. A `MergeReviewStore` holds the review state. On commit
it calls `core.apply.applyDecisions(contacts, proposals, decisions)` (Plan 2) and
writes the merged `List<Contact>` back to
`AppStore` as `mergedContacts`, which the Deletion step (4c) consumes. The engine
(matcher, merger, applyDecisions) is already built and tested in `core`; 4b is
store + UI.

## 4. Review store

Package `com.robsartin.contactotomy.ui` (depends on `core`; Konsist keeps `core`
UI-free).

```kotlin
enum class Origin { HIGH, UNCERTAIN, MANUAL }
enum class Decision { ACCEPT, REJECT, SKIP }

data class ReviewItem(
    val id: String,                  // stable: cluster id, or a manual id
    val origin: Origin,
    val proposal: MergeProposal,     // core.merger: cluster, merged, provenance, conflicts
    val decision: Decision,
    val excludedValues: Set<ExcludedValue> = emptySet(),  // core.apply
    val conflictChoices: Map<String, String> = emptyMap(),
)

data class MergeReviewState(val items: List<ReviewItem>, val committed: Boolean = false)
```

`MergeReviewStore(contacts, matcher, merger)` builds initial items on construction:
HIGH clusters (decision = ACCEPT), uncertain pairs as 2-card proposals
(decision = REJECT). Exposes a `StateFlow<MergeReviewState>` and intents:

- `setDecision(itemId, Decision)`, `toggleField(itemId, ExcludedValue)`,
  `chooseConflict(itemId, field, value)`.
- `acceptAllHighConfidence()` — set every HIGH item to ACCEPT.
- `manualMerge(contactIds)` (4b-2) — require ≥2 ids; build a `Cluster`, run
  `ContactMerger.merge`, append `ReviewItem(MANUAL, ACCEPT)`. Block selecting a
  card already consumed by an accepted item (surfaced error, no crash).
- `removeManual(itemId)` (4b-2).
- `commit()` — build `MergeDecision`s from ACCEPT items, call
  `applyDecisions(contacts, acceptedProposals, decisions)`, return the merged
  list, set `committed = true`. REJECT/SKIP leave their cards unmerged.

**Decision semantics:** REJECT = "not the same person, keep separate" (final);
SKIP = "decide later" — also unmerged on commit, but visually distinct.

**Double-touch guard:** if two accepted proposals share a contact id (possible
once manual merge exists), `commit()` is deterministic — first-accepted wins, the
other downgrades to SKIP with a surfaced note; a contact is never double-merged.

Everything is pure/deterministic for unit tests.

## 5. UI

**Left — cluster list**, three labeled sections:
- **To merge (HIGH):** pre-accepted rows (name, # cards, ✓); a section "Accept all"
  button → `acceptAllHighConfidence`.
- **Possible matches (UNCERTAIN):** default reject; rows show both names + the
  why-matched reason.
- **Manual (4b-2):** user-built, each removable.
Each row shows its decision (✓ / ✕ / ◷) and loads the detail when selected. A
running summary: "Will merge N clusters → M contacts."

**Right — detail pane:** before cards (primary highlighted + stacked secondaries
with source badges + dates) → merged card where each value is an include/exclude
chip with provenance, single-value conflicts as inline radios (prefer-newest
pre-selected), most-complete name. Per-item Accept / Reject / Skip. Toggles call
`toggleField` / `chooseConflict`.

**Manual merge (4b-2):** a "Merge cards manually…" mode over the full contact list
with a name/email text filter (a plain `LazyColumn`, no search index for MVP);
tick ≥2 cards → confirm → a MANUAL item appears, selected for review.
Already-consumed cards are disabled with a tooltip.

**Commit:** the wizard Next becomes "Apply merges & continue" → `commit()` → writes
`mergedContacts` to `AppStore` → advances to Deletion.

Composables stay thin over the store; only platform glue (if any) is excluded from
coverage.

## 6. Testing

- **`MergeReviewStore` unit tests** (no Compose): initial items from a fixture
  contact set (HIGH accepted, UNCERTAIN rejected); setDecision/toggleField/
  chooseConflict; acceptAllHighConfidence; commit() result + determinism;
  double-touch downgrade; (4b-2) manualMerge builds an item, consumed-card guard
  blocks, removeManual.
- **Compose UI tests** (`runComposeUiTest`): sections render with counts; selecting
  a row loads the detail; toggling a chip / flipping a conflict updates the merged
  preview; Accept-all flips HIGH rows; (4b-2) manual selection adds an item;
  "Apply merges & continue" commits and advances.
- Konsist keeps `core` UI-free. Coverage floors line ≥90 / branch ≥65 (ADR-0012).

## 7. Scope

**4b-1 (issue #10):** `MergeReviewStore` (HIGH + UNCERTAIN), list+detail UI,
merged-card toggles/conflicts, bulk accept, commit → `mergedContacts`.
**4b-2 (later issue):** manual merge (selection mode + `manualMerge`/`removeManual`
+ consumed-card guard).
**Out of scope:** deletion screen (4c), export (4d), usage signals.

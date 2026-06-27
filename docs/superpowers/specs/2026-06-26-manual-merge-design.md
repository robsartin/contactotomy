# Manual Merge (4b-2) — Design Spec

Date: 2026-06-26
Status: Approved (design); pending implementation plan
Builds on: Plan 4b-1 merge review and the merge-screen redesign
(`MergeReviewStore`, `MergeScreen`, `2026-06-26-merge-redesign-design.md`),
ADR-0012/0013. core engine (Plan 2) unchanged.
Tracking issue: #32

## 1. Purpose

The matcher only proposes merges it is confident enough about (HIGH clusters) or
flags as uncertain pairs. Some real duplicates share nothing the matcher keys on
(no shared phone/email, names too different) and are never clustered at all. This
feature lets the user **force-merge two or more contacts the matcher missed**,
directly inside the redesigned merge screen, reusing the existing review flow.

## 2. Decisions

- **No `core` changes.** A manual merge is just another `ReviewItem` with
  `Origin.MANUAL`. The store assembles a `Cluster` from the chosen contacts, runs
  the existing `merger.merge(cluster)`, and appends the resulting proposal to
  `items` as `PENDING`. From there it uses the *same* accept / keep-separate /
  commit / name-choice / checkbox / radio machinery as auto items. (Building a
  `core` `Cluster` and calling `merger.merge` in the store is already how
  `buildItems()` works — no new `core` dependency.)
- **Searchable add-list selection.** Contacts can number in the hundreds, so
  assembly is search-driven, not a flat checkbox roster.
- **Eligible pool = un-clustered cards only.** The picker shows only contacts that
  are not a member of any existing item (HIGH, uncertain, or already-created
  manual). This makes member overlap impossible, so manual merges never exercise
  the `commit()` double-touch guard (which stays as defense).
- **Manual items behave like auto items.** Same decision controls (Accept merge /
  Keep separate), same Undo from Resolved, same detail pane. Consistent UI.
- **`acceptAllHighConfidence()` does not sweep manual items** — it is keyed on
  `origin == Origin.HIGH`, and manual items are `Origin.MANUAL`.

## 3. State model (`MergeReviewTypes`)

No changes. `Origin.MANUAL` already exists; `ReviewItem` already carries
everything a manual item needs (`proposal`, `decision`, `excludedValues`,
`conflictChoices`, `nameChoiceId`).

## 4. Store (`MergeReviewStore`) — two additions

- `eligibleForManualMerge(): List<Contact>` — the searchable pool: every `contact`
  whose `id` is not a member of any current `item.proposal.cluster`. Computed from
  the live `_state.value.items` (so cards consumed by a just-created manual item
  drop out of the pool immediately).

- `manualMerge(memberIds: List<String>): String` —
  - require at least 2 distinct ids, all drawn from the eligible pool; otherwise
    no-op / ignore non-eligible ids (defensive — the UI only offers eligible ids).
  - resolve ids to `Contact`s from `contacts`.
  - build `Cluster(id = "manual-" + sortedIds.joinToString("+"),
    members = thoseContacts, confidence = Confidence.HIGH /* user-asserted */,
    reasons = listOf("Manually merged"))`.
  - `proposal = merger.merge(cluster)`; append
    `ReviewItem(id = cluster.id, origin = Origin.MANUAL, proposal = proposal)`
    (decision defaults to `PENDING`) to `items`.
  - return the new item id so the screen can auto-select it.

`commit()` is unchanged: it already merges every `ACCEPT` item regardless of
origin and applies name overrides by merged-contact id.

> Note on `confidence`: the manual `Cluster` is tagged `HIGH` because the user
> asserted the match. This is metadata on the proposal only; the UI distinguishes
> manual items by `Origin.MANUAL`, not by cluster confidence. Implementation must
> confirm `merger.merge` does not branch on `confidence` in a way that changes the
> merged result (it does not today).

## 5. Screen (`MergeScreen`)

- **"+ Manual merge" button** in the "Needs review" header, alongside "Accept all
  high-confidence".
- **In-window picker panel** (a state-toggled composable, not an OS
  `DialogWindow` — keeps it inside `runComposeUiTest`):
  - a search `TextField` filtering `eligibleForManualMerge()` by name / phone /
    email (case-insensitive substring),
  - a `Checkbox` row per result (name + a source badge),
  - a "Selected (k)" summary,
  - **"Create merge"** enabled at k ≥ 2, and **Cancel**.
- Picker-open flag, search query, and selected-id set are transient `remember`
  state in the composable — *not* store domain state (YAGNI).
- On **Create merge** → `val id = store.manualMerge(selectedIds)`, close the
  panel, select the returned item. It appears in "Needs review" tagged
  **"manual"**; its detail pane and decision buttons are identical to auto items.

Composables stay thin over `MergeReviewStore`.

## 6. Testing

- **`MergeReviewStore`:** `manualMerge` appends a `PENDING` `MANUAL` item whose
  proposal merges exactly the chosen members; fewer than 2 ids is a no-op;
  `eligibleForManualMerge()` excludes cards already in a HIGH cluster, an uncertain
  pair, or a previously-created manual item; accepting then `commit()`-ing a manual
  item collapses those cards in the result.
- **Compose UI** (`runComposeUiTest`): the "+ Manual merge" button opens the
  picker; typing in search filters the eligible list; selecting 2 and pressing
  "Create merge" closes the picker and shows a manual-tagged item, auto-selected;
  accepting it and committing reduces the contact set.
- **End-to-end** (`AppFlowTest`): one case that manually merges two un-clustered
  fixture cards through the full Import→Merge→Deletion→Export flow and asserts a
  smaller `finalContacts`. (Add a clearly-distinct un-clustered pair to
  `fixtures/duplicates.vcf` if the current five don't provide one.)
- Konsist keeps `core` UI-free; coverage floors line ≥ 90 / branch ≥ 70 unchanged.

## 7. Scope

In scope: `MergeReviewStore` (`+ eligibleForManualMerge`, `+ manualMerge`),
`MergeScreen` (button + picker panel + manual tag), their tests, and possibly a
fixture addition for the e2e case. `MergeReviewTypes` needs no change. The `core`
matcher/merger/apply engine is unchanged. Out of scope: #29 (name + company
choice), #30 (visual polish), deletion/export screens.

## 8. Known limitation

Because eligibility excludes cards in *any* item, a manual merge later set to
"Keep separate" leaves its member cards out of the re-pick pool (they remain
members of a rejected item). This is the consistent-lifecycle trade-off chosen
over a dedicated "Remove" control. Minor; revisit only if it bites in real use.

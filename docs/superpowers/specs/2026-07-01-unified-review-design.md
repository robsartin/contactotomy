# Unified "Review every card" step (Merge + Tidy) — Design Spec (#78)

Date: 2026-07-01
Status: Approved (design)
Tracking issue: #78 (supersedes #76)

## 1. Purpose

Collapse the separate **Merge** and **Tidy** wizard steps into a single **Review**
step with **two sections in one screen**, so duplicates and single-card cleanups are
handled in one place. This is the **foundation**: behavior is preserved (it does
exactly what Merge + Tidy did today, unified). The richer capabilities are separate
follow-ups (#79 editing, #80 smarter defaults/dedupe, #81 preview/discard, #82
keyboard).

## 2. Wizard change

- `Screen`: `IMPORT, REVIEW, DELETION, EXPORT` — `MERGE` and `TIDY` are removed,
  replaced by `REVIEW` (same position: between IMPORT and DELETION).
- `StepIndicator` labels: Import · Review · Deletion · Export.
- `TidyScreen` and `TidyStore` are deleted. The `core` transforms they used
  (`CompanyNormalizer.markAsCompany`, `CompanyNormalizer.nameFromEmail`,
  `companyNameText`, `CompanyNameDetector`) stay in `core` and are reused by the
  Review store.

## 3. State (`AppState` / `AppStore`)

- Replace the two fields `mergedContacts` and `tidyContacts` with a single
  `reviewedContacts: List<Contact>?`.
- `AppStore`: `setReviewedContacts(list)` replaces `setMergedContacts` and
  `setTidyContacts`.
- `workingContacts(state) = finalContacts ?: reviewedContacts ?: contacts`.
- Deletion source = `reviewedContacts ?: contacts`.
- `exportSummary` (from #67) must be updated: the "merged" and "removed" deltas now
  derive from `contacts` → `reviewedContacts` → `finalContacts` (drop the
  tidyContacts stage). Keep the same displayed numbers' meaning: merged = collapsed
  by review; removed = deleted by rules.

## 4. The Review store (`ui/ReviewStore.kt`, new)

Composes the existing, well-tested merge review with singleton tidy. **Do not rewrite
`MergeReviewStore`** — reuse it.

- Constructor `ReviewStore(contacts: List<Contact>)`:
  - Holds an internal `MergeReviewStore(contacts)` for **Section 1 (Duplicates to
    merge)** — clusters + uncertain pairs + manual merge, unchanged.
  - Computes **Section 2 (Cards to clean)** = **singletons** with a suggested tidy
    action. A *singleton* is a contact whose id is NOT a member of any HIGH cluster
    and NOT in any uncertain pair (i.e. it never appears in Section 1). A singleton is
    *suggested* when `CompanyNameDetector.isHighPrecision(name)` **or**
    (`companyNameText(name)` blank && `org` blank && `emails` non-empty) — the same
    rule the old `TidyStore.suggested` used. Section 2 lists only suggested singletons
    (these are the "cards to clean"); pre-marked by default.
  - Section-2 state: `markedIds: Set<String>` (initialized to the suggested set);
    intent `toggleClean(id)`. Reuse `TidyAction`/`actionFor` semantics for the
    per-card transform preview (COMPANY → org; EMAIL_NAME → name from email).
- Expose the internal merge store (or delegate its intents) so the screen can drive
  Section 1 exactly as `MergeScreen` does today.
- `commit(): List<Contact>`:
  1. `val merged = mergeStore.commit()` (Section 1 result — merges applied, others
     pass through, order preserved).
  2. Map over `merged`: for any contact whose id is in `markedIds`, apply
     `when (actionFor(c)) { EMAIL_NAME -> nameFromEmail(c); COMPANY -> markAsCompany(c) }`;
     others unchanged. (Marked singletons pass through Section 1 untouched, so their
     ids still exist in `merged`.)
  Return the combined list.

### Behavioral split (decided)
Company-name promotion *inside a merge group* is already handled by
`MergeReviewStore.companyAutoSuggest` (name=person, org=company) on accept. So
**Section 2 covers only singletons**; merged cards tidy themselves via the merge.
Cards in an uncertain pair that the user rejects are not re-offered for tidy in this
pass (a minor change from the old post-merge Tidy; acceptable for the foundation).

## 5. The Review screen (`ui/ReviewScreen.kt`, new; replaces MergeScreen/TidyScreen usage)

Two clearly-labeled sections in one screen (stacked, each independently scrollable;
visible scrollbars are polished app-wide later in #84):

- **Section 1 — "Duplicates to merge":** reuse the existing merge list + detail-pane
  composables (cluster/pair list, source cards, per-field pills, name/org/conflict
  choices, Accept / Keep separate / Delete). Factor the current `MergeScreen` body
  into reusable pieces if needed rather than duplicating.
- **Section 2 — "Cards to clean":** the old Tidy rows — a checkbox (`mark:<id>`) per
  suggested singleton, the card name + `SourceBadge`, and the `→ org:` / `→ name:`
  action hint; a detail pane/inline detail as today.
- Keep existing testTags where possible so existing UI tests port with minimal churn;
  add section headers ("Duplicates to merge", "Cards to clean").
- Empty states: if Section 1 has no items, show "No duplicates found"; if Section 2
  has none, "No single cards need cleaning."

## 6. Wiring (`App.kt`)

- Hoist `ReviewStore(state.contacts)`.
- Next on `Screen.REVIEW` → `store.setReviewedContacts(reviewStore.commit()); store.next()`.
- Remove the `MERGE`/`TIDY` branches and the `mergeStore`/`tidyStore`/`tidySource`
  hoists; deletion source becomes `state.reviewedContacts ?: state.contacts`.
- `ReviewScreen(reviewStore)` renders for `Screen.REVIEW`.

## 7. Tests

- **`ReviewStore`** (new): singletons-with-suggestion are listed + pre-marked;
  non-suggested singletons and grouped cards are NOT in Section 2; `toggleClean`
  flips a mark; `commit()` applies BOTH merge decisions and singleton tidies (build a
  fixture with a duplicate pair + a lone company card + a lone nameless-email card;
  assert the merged+tidied output). Reuse existing merge-store test patterns for
  Section 1 behavior (don't re-test MergeReviewStore internals — it keeps its tests).
- **`ReviewScreen`** (new): both section headers render; a merge item can be accepted;
  a clean row can be toggled and shows its hint; empty states.
- **`AppFlowTest`**: update all 9 flows to `Import → Review → Deletion → Export`
  (one fewer Next; the merge + tidy actions now happen on the single Review screen).
  Preserve the existing assertions (final counts, ORG/FN on export, no-reply deletion,
  email→name, company-only). E.g. the "nameless email card named from email" flow now
  marks it in Review's Section 2 instead of a separate Tidy step.
- Delete `TidyScreenTest` / `TidyStoreTest` (folded into ReviewStore/ReviewScreen tests).
- Update `exportSummary` tests for the `reviewedContacts` field rename.
- `./gradlew check` green: line ≥90 / branch ≥70, Spotless/ktlint, Konsist (core
  stays UI-free; `CompanyNormalizer`/`CompanyNameDetector` remain in core).

## 8. Scope

In scope: the wizard collapse, `ReviewStore`, `ReviewScreen`, `AppState` field
rename, `App.kt` rewiring, deletion of `TidyScreen`/`TidyStore`, and all test updates
— behavior-preserving. Out of scope (separate issues): #79 (full card editing), #80
(smarter defaults + dedupe + no-name→email/phone generalization + mark-as-company on
any card), #81 (result preview + discard-cluster), #82 (keyboard), #84 (visual).

## 9. Notes

- Reuse over rewrite: `MergeReviewStore` and its tests are untouched; `ReviewStore`
  composes it. The old `TidyStore` logic (`suggested`, `actionFor`, transforms) moves
  into `ReviewStore`.
- Keep the diff behavior-preserving so the ported `AppFlowTest` assertions still hold;
  that is the safety net for this refactor.

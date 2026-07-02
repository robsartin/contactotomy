# Review: smarter defaults & auto-cleanup (#80) — Implementation Plan

> Pure TDD, checkbox steps, each task ends `./gradlew check` green + commit.

**Goal:** (A) no-name→email/phone name fallback, (B) mark-a-merged-card-as-company in
Review, (C) auto-dedupe emails/addresses on merges.

**Tech:** Kotlin 2.0.21, Compose Desktop, StateFlow, kotlin.test + Compose UI test.

## Global Constraints
- Pure TDD; additive. Coverage floors line ≥90 / branch ≥70 (never lower).
- `core` UI-free (Konsist). Spotless/ktlint + Konsist green.
- B reuses the #79 override intents (`setNameOverride`, `setOrgOverride`) — no new model.
- Commit messages end `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch `80-review-smart-defaults`. First commit: copy spec →
  `docs/superpowers/specs/2026-07-01-review-smart-defaults-design.md` and this plan →
  `docs/superpowers/plans/2026-07-01-review-smart-defaults.md`. Read the spec first.

---

### Task 1: core — `nameFromPhone` + merger dedupe
**Files:** modify `core/company/CompanyNormalizer.kt`, `core/merger/ContactMerger.kt`;
tests `core/company/CompanyNormalizerTest.kt`, `core/merger/ContactMerger*Test.kt`.

- [ ] **Step 1: Failing tests** — `nameFromPhone(c)` sets `name.formatted` = first
  phone, leaves phones/emails intact; `ContactMerger` merged card dedupes case-variant
  emails (`Bob@X.com` + `bob@x.com` → one, first casing kept) and duplicate addresses
  across members (same `toDisplayString()` → one).
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement `nameFromPhone` (mirror `nameFromEmail`); in `ContactMerger`
  make the emails union case-insensitively distinct (preserve first) and addresses
  distinct by `toDisplayString()` (preserve first/order).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 2: `ReviewStore` — PHONE_NAME action
**Files:** modify `ui/ReviewStore.kt`; test `ui/ReviewStoreTest.kt`.

- [ ] **Step 1: Failing tests** — `TidyAction` gains `PHONE_NAME`; a nameless card with
  no email but a phone is a suggested clean candidate with `actionFor == PHONE_NAME`;
  `commit()` names it from its phone; a nameless card WITH an email is still
  `EMAIL_NAME` (regression); a company-named card is still `COMPANY`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement per spec §3: extend `suggested`, `actionFor`, and `commit`'s
  dispatch (`PHONE_NAME -> CompanyNormalizer.nameFromPhone(c)`).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 3: UI — Mark-as-company button + phone-name hint + gate
**Files:** modify `ui/MergeScreen.kt` (detail: mark-as-company button) and
`ui/ReviewScreen.kt` (Section-2 hint for PHONE_NAME); tests
`ui/MergeScreen*Test.kt` / `ui/ReviewScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — a "Mark as company" button (testTag
  `mark-as-company`) on a selected merged item clears the name and sets org to the
  company text of the effective name; `commit()` yields org set + name cleared. A
  Section-2 clean row for a phone-name card shows `→ name: <first phone>`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement: the button calls
  `store.setOrgOverride(item.id, companyNameText(store.effectiveName(item)).ifBlank { displayName(store.effectiveName(item)) })`
  then `store.setNameOverride(item.id, ContactName())`; the Review Section-2 hint adds
  the `PHONE_NAME -> "→ name: ${c.phones.first()}"` case. Match theme/`Dimens`.
- [ ] **Step 4:** `./gradlew check` fully green.
- [ ] **Step 5:** Commit. Open PR `Closes #80`.

## Self-review
- `nameFromPhone` + dedupe in `core` (Konsist green); PHONE_NAME wired through
  suggested/actionFor/commit; mark-as-company reuses #79 overrides (no new model);
  coverage floors held; no-email/company regressions pass.

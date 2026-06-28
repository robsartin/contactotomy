# Phase 2 Matcher (person↔company-name) — Implementation Plan (#68)

> **For agentic workers:** implement task-by-task with pure TDD. Steps use checkbox
> (`- [ ]`) syntax. Each task ends with `./gradlew check` green and a commit.

**Goal:** Detect person↔company-name duplicates (one card's *name* is a high-precision
company, sharing a phone/email with a person card) and surface them as UNCERTAIN
review pairs. Detection-only; the existing review + `companyAutoSuggest` handle the merge.

**Architecture:** One new `MatchReason` and one new rule at the top of
`EdgeClassifier.classify`. No other production change — UNCERTAIN edges already flow
through `ContactMatcher` → `MergeReviewStore`.

**Tech Stack:** Kotlin 2.0.21, kotlin.test, Compose UI test (e2e).

## Global Constraints

- Pure TDD: failing test first → confirm fail → minimal implementation → confirm green → commit.
- Production change stays in `core/matcher` (Konsist: core UI-free). `CompanyNameDetector`
  is an `object` — call statically, no constructor change to `EdgeClassifier`.
- Coverage floors: line ≥90, branch ≥70 (never lower); cover new branches.
- Spotless/ktlint + Konsist pass (`./gradlew check`); `./gradlew spotlessApply` if needed.
- YAGNI: only what the spec lists; do not touch the merger/merge-UI/exporter.
- Commit messages end with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch: `68-phase2-matcher`. First commit: copy the design spec into
  `docs/superpowers/specs/2026-06-27-phase2-matcher-design.md` and this plan into
  `docs/superpowers/plans/2026-06-27-phase2-matcher.md`.

---

### Task 1: COMPANY_MATCH reason + EdgeClassifier rule

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypes.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifier.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifierTest.kt` (existing — add cases)

**Produces:** `MatchReason.COMPANY_MATCH`; the Phase-2 rule.

- [ ] **Step 1: Failing tests** in `EdgeClassifierTest` (use the existing `contact()`
  factory; "Acme Inc" is high-precision via legal suffix):
  - person ("Robert Sartin") + company-name ("Acme Inc") sharing a phone →
    `classify` returns a non-null edge, `confidence == UNCERTAIN`, `reasons` contains
    `SHARED_PHONE` and `COMPANY_MATCH`.
  - same but sharing an email → `reasons` contains `SHARED_EMAIL` and `COMPANY_MATCH`.
  - person + "Acme Inc" with NO shared phone/email → `null`.
  - person + a non-high-precision company-ish name (e.g. plain "Plumbing") sharing a
    phone → NOT a Phase-2 pair: assert the result is what the existing rules give
    (likely `null` due to given-name conflict). (Confirms high-precision gate.)
  - regression: two persons "Robert Sartin" / "Bob Jones" (given conflict) → `null`.
  - regression: two "Robert Sartin" sharing a phone → `HIGH` (unchanged).
  - If any existing test asserted a person↔company pair returns `null`, update it to
    expect the new `UNCERTAIN` edge (note which in the commit message).
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement per design §3a/§3b: append `COMPANY_MATCH` to the enum;
  add the XOR rule at the very top of `classify` (before the existing Rule 1).
- [ ] **Step 4:** Run, confirm pass; run the whole `core/matcher` test package to catch
  regressions.
- [ ] **Step 5:** Commit.

### Task 2: Matcher + review integration

**Files:**
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcherTest.kt` (existing — add a case)
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreTest.kt` (existing — add a case)

- [ ] **Step 1: Failing tests.**
  - `ContactMatcher`: given a person + a high-precision company sharing an email,
    `match(...).uncertainPairs` contains the pair (and it is not inside a HIGH cluster).
  - `MergeReviewStore`: build a store from those two contacts; the uncertain review
    item has `companyAutoSuggest` applied — `orgChoice == "Acme Inc"` and `nameChoiceId`
    is the person's id (name=person). (Mirror existing company-suggest assertions.)
    Note: ensure NEITHER input card has a non-blank `org` (else `companyAutoSuggest`
    intentionally declines).
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** No production change expected — the routing + auto-suggest already
  exist. If a test fails for a real reason, fix the smallest cause (but do NOT modify
  the merger/exporter; if you believe a production change is truly needed, STOP and
  report rather than guessing).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 3: e2e + fixture + gate

**Files:**
- Create: `src/test/resources/fixtures/phase2-company-person.vcf`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt` (add one flow)

- [ ] **Step 1: Create the fixture** — two vCard 3.0 cards sharing an email, no org:
  - Card A: `FN:Jane Smith`, `N:Smith;Jane;;;`, `EMAIL:jane@example.com`.
  - Card B: `FN:Acme Inc`, `N:;;;;` (or `N:Acme Inc;;;;`), `EMAIL:jane@example.com`.
  (Match the format of existing fixtures like `name-company.vcf`; both cards must have
  NO `ORG` line so `companyAutoSuggest` will propose the promotion.)
- [ ] **Step 2: Failing e2e test** in `AppFlowTest` (mirror the existing
  "manual merge promotes a company name into org and exports it" test, but WITHOUT the
  manual-merge clicks — the pair is auto-detected):
  - import the fixture (2 contacts), advance to Merge, the uncertain pair is present;
    select it, `Accept merge`; Next → Tidy → Next → Deletion → Next → Export.
  - assert `finalContacts` has size 1, `org == "Acme Inc"`, name is "Jane Smith";
    export contains `ORG:Acme Inc` and `FN:Jane Smith`, not `FN:Acme Inc`.
  - Use the same testTag/onNodeWithText patterns as existing AppFlowTest cases.
- [ ] **Step 3:** Run, confirm fail; then make it pass (wiring already supports it;
  adjust selectors/fixture if needed).
- [ ] **Step 4:** `./gradlew check` fully green.
- [ ] **Step 5:** Commit. Open PR to `main` with `Closes #68`.

## Self-review checklist (before PR)
- Spec coverage: reason+rule (T1), matcher/review integration (T2), e2e+fixture (T3). ✓
- No production change outside `core/matcher` (T2/T3 are tests + a fixture).
- New branches covered; coverage floors held; Konsist green (core UI-free).
- `COMPANY_MATCH` appended last in the enum (ordinals of existing reasons unchanged).

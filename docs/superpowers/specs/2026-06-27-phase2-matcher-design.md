# Phase 2 Matcher — person↔company-name duplicates — Design Spec (#68)

Date: 2026-06-27
Status: Approved (design)
Tracking issue: #68
Builds on: name-gated matcher (#29), CompanyNameDetector, companyAutoSuggest (#36).

## 1. Purpose

The matcher is name-gated: `EdgeClassifier` Rule 1 returns `null` the moment two
given names conflict. So a person card ("Robert Sartin") and a card whose **name**
is really a company ("Indeed") never pair, even when they share an email — because
"Robert" vs "Indeed" reads as a given-name conflict. Phase 2 detects this specific
shape: **exactly one side's name is a high-precision company name, and the two share
a phone or email** → surface as an UNCERTAIN review pair.

Detection only. The existing review flow + `companyAutoSuggest` already propose the
right merge (name = the person, org = the company name), so there is **no merger,
merge-UI, or exporter change.**

## 2. Scope boundary (what it does NOT do)

- It keys off the **name** field being a company, NOT the **org** field. Two
  "Robert Sartin" cards with orgs "Indeed" vs "Find Help" are unaffected by Phase 2
  (both names are the person) — they are handled by the existing person↔person rules
  (HIGH if they share contact, else UNCERTAIN name-only), and their differing org is
  surfaced as a normal field conflict.
- Two company-named cards (both high-precision) are out of scope — they fall through
  to the existing rules unchanged.
- A weak/keyword company-ish name does NOT trigger Phase 2 (high-precision only).

## 3. The change — confined to `core/matcher`

### 3a. `MatchTypes.kt`
Add `COMPANY_MATCH` to `enum class MatchReason` (append at the end to avoid
reshuffling existing ordinals used in sorting).

### 3b. `EdgeClassifier.classify` — new rule at the very TOP
`CompanyNameDetector` is an `object` (already called statically across the codebase),
so call it statically; no constructor change.

```kotlin
// Phase 2: person <-> company-name. Runs BEFORE the given-name conflict gate,
// because comparing a person name to a company name is meaningless.
val aCompany = CompanyNameDetector.isHighPrecision(a.name)
val bCompany = CompanyNameDetector.isHighPrecision(b.name)
if (aCompany != bCompany) {                    // exactly one side is a strong company name
    val sharedPhone = a.phones.any { it in b.phones }
    val sharedEmail = a.emails.any { it in b.emails }
    if (sharedPhone || sharedEmail) {
        val reasons = buildList {
            if (sharedPhone) add(MatchReason.SHARED_PHONE)
            if (sharedEmail) add(MatchReason.SHARED_EMAIL)
            add(MatchReason.COMPANY_MATCH)
        }
        return MatchEdge(a, b, Confidence.UNCERTAIN, reasons)
    }
    return null                                // company-name but no shared contact -> don't pair
}
// otherwise: fall through to the existing Rules 1..5 unchanged
```

Behavior of the XOR guard:
- two persons → `false != false` → skipped → existing rules apply (unchanged).
- two high-precision companies → `true != true` → skipped → existing rules (out of scope).
- person + high-precision company → intercepted: paired UNCERTAIN iff shared contact.

### 3c. No other production change
`ContactMatcher` already routes UNCERTAIN edges into `uncertainPairs` (and drops
those already unified inside a HIGH cluster). `MergeReviewStore.buildItems` maps each
uncertain pair to a `ReviewItem` via `reviewItem()`, which applies `companyAutoSuggest`
— so a Jane Smith ↔ Acme Inc pair is proposed as name=Jane Smith, org=Acme Inc.

## 4. Known limitation (acceptable, document only)

A company card sharing a main phone/email with N person cards yields N UNCERTAIN
pairs (company↔each person). All are reviewable/rejectable; not auto-merged. Not
fixed in this issue.

## 5. Testing

- **`EdgeClassifier`**:
  - person + high-precision company ("Acme Inc") sharing a **phone** → `UNCERTAIN`
    with reasons containing `SHARED_PHONE` and `COMPANY_MATCH`.
  - same but sharing an **email** → reasons contain `SHARED_EMAIL` + `COMPANY_MATCH`.
  - person + high-precision company with **no** shared contact → `null`.
  - person + **weak/keyword** company-ish name sharing contact → NOT a Phase-2 pair
    (high-precision only) → falls through (assert it is null / unchanged).
  - regression: two persons with conflicting given names → still `null`.
  - regression: two persons, same given + shared contact → still `HIGH` (unchanged).
  - Update any existing test that asserted a person↔company pair returns `null` — the
    new expected behavior is an `UNCERTAIN` edge.
- **`ContactMatcher`**: a list with a person and a high-precision company sharing an
  email produces a pair in `MatchResult.uncertainPairs`.
- **`MergeReviewStore`**: that uncertain pair becomes a review item with auto-suggest
  name=person, org=company (assert `nameChoiceId`/`orgChoice`).
- **`AppFlowTest` e2e**: a new fixture `phase2-company-person.vcf` with
  "Jane Smith" (email jane@example.com, no org) and "Acme Inc" (in FN, email
  jane@example.com shared, no org). Import → Merge shows the auto-detected uncertain
  pair → accept → Tidy → Deletion → Export → exactly one contact with `FN:Jane Smith`
  and `ORG:Acme Inc`.
- `./gradlew check` green: line ≥90 / branch ≥70 (cover the new branches),
  Spotless/ktlint, Konsist (change stays in `core/matcher`, core UI-free).

## 6. Scope

In scope: `MatchReason.COMPANY_MATCH`; the Phase-2 rule in `EdgeClassifier`; tests;
the e2e fixture. Out of scope: any merger/merge-UI/exporter change; company↔company
dedup; org-field-based association; the shared-main-line fan-out limitation.

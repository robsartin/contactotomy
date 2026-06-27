# Empty / Company-only Name in the Merge Flow — Design Spec (#36)

Date: 2026-06-27
Status: Approved (design); pending implementation plan
Builds on: #29 name+company choice (`CompanyNameDetector`, `orgChoice` override,
`CompanyOrgField`) and #30 visual polish (restyled `MergeScreen`).
Tracking issue: #36. Companion: #37 (Phase B — reviewed mark-as-company for
standalone cards, separate spec).

**Depends on #30 (PR #35) merging first** — this touches the restyled
`MergeScreen`/`MergeReviewStore`; implement on a branch off `main` after #35 lands.

## 1. Purpose

A merged contact should be able to have **no person name**, so it can be a
company-only card (blank name, org set). Two real cases:
- A person card + a company-mis-filed-as-name card already merge with name+org
  chosen (#29), but a cluster whose only names are company-like ends up
  duplicating the company string into both `name` and `org`.
- A card like "Round Rock ISD" (given "Round", middle "Rock", family "ISD") is a
  company the user wants as org with no person name — and the detector misses it
  ("ISD" is not a known suffix/keyword, not all-caps).

So we need: an **unconditional "(none)" name option**, the ability to **promote
any source name to org** (not just detector-flagged ones), and a **company-only
auto-suggest** that clears the name. Plus two detector keyword additions.

## 2. Decisions

- **`(none)` Name option is unconditional** — selectable on any merge, independent
  of org/company. Add `ReviewItem.nameCleared: Boolean = false` (mirrors how
  `orgChoice` models "user chose none").
- **Promote ANY source name to org.** `CompanyOrgField` lists every source card's
  name as a "(from name)" candidate (deduped against existing org values), so
  detector-missed companies (e.g. "Round Rock ISD") are promotable manually. The
  detector still drives the "looks like a company" badge and the auto-suggested
  default; it no longer gates which names are promotable.
- **Company-only auto-suggest clears the name.** When a cluster has a company-like
  name and **no** non-company person name, auto-suggest sets `nameCleared = true`
  and `orgChoice = company name` (no `nameChoiceId`). The person+company case is
  unchanged (`nameChoiceId` + `orgChoice`).
- **Detector keywords:** add `ISD` (a keyword token, so "Round Rock ISD" matches)
  and the phrase `Independent School District` (case-insensitive substring) to
  `CompanyNameDetector`.
- **No matcher/merger/apply/exporter change.** The only `core` edit is the
  detector keyword list (not the matcher). Export already serializes an empty name
  cleanly.

## 3. State model (`MergeReviewTypes`)

Add to `ReviewItem`:

```kotlin
val nameCleared: Boolean = false,   // true => merged contact has an empty name
```

`nameChoiceId` and `nameCleared` are mutually exclusive in effect: if
`nameCleared`, the merged name is emptied regardless of `nameChoiceId`.

## 4. Store (`MergeReviewStore`)

- **`clearName(itemId)`** — sets `nameCleared = true` (leave `nameChoiceId` as-is;
  cleared wins in `commit`).
- **`chooseName(itemId, memberId)`** — now also sets `nameCleared = false` (picking
  a real name un-clears).
- **`companyAutoSuggest(members)`** — change its return to also carry
  `nameCleared`. Logic: if no member has an org and some member's name is
  company-like:
  - if a different member has a non-company, non-blank name → `nameChoiceId =
    that member`, `nameCleared = false`, `orgChoice = company name` (today's
    behavior);
  - else (company-only) → `nameChoiceId = null`, `nameCleared = true`,
    `orgChoice = company name`.
  Otherwise → no suggestion (`null/false/null`). Apply via the existing
  `reviewItem(...)` builder so `buildItems` and `manualMerge` both get it.
- **`commit()`** — in the name-override step: for each accepted item, if
  `nameCleared` set the merged contact's `name = ContactName()` (empty); else apply
  the `nameChoiceId` override as today. (Org override unchanged.)

## 5. Detail-pane UI (`MergeScreen`)

- **Name `FieldGroup`:** add a **"(none)"** radio option below the source-name
  radios. Selected when `item.nameCleared`. Selecting it calls `store.clearName`;
  selecting a source name calls `store.chooseName` (which un-clears). The existing
  "looks like a company" badges on source names stay.
- **`CompanyOrgField`:** build the promotable candidates from **all** source-card
  names (deduped against existing non-blank source orgs), each tagged "(from
  name)", regardless of `CompanyNameDetector`. Keep the existing org values and the
  "(none)" org option. (Drop the `detect(...) != null` filter on promotions;
  detection no longer limits promotability.)

## 6. Detector (`core/company/CompanyNameDetector`)

- Add `ISD` to the `KEYWORDS` set (token match → "Round Rock ISD" → KEYWORD).
- Add a phrase check: if the display string contains "independent school
  district" (case-insensitive) → `KEYWORD`. Place it with the other KEYWORD logic
  (after AMPERSAND, before WEAK), consistent with the existing precedence.

## 7. Testing

- **`CompanyNameDetector`:** "Round Rock ISD" → KEYWORD; "Pflugerville
  Independent School District" → KEYWORD; an ordinary name still → null.
- **`MergeReviewStore`:** `clearName` sets the flag; `chooseName` un-clears;
  `commit()` empties the name when cleared (assert merged `name == ContactName()`);
  company-only auto-suggest sets `nameCleared` + `orgChoice` and no `nameChoiceId`;
  person+company auto-suggest still sets `nameChoiceId` (not cleared).
- **Compose UI** (`runComposeUiTest`): the Name group shows a "(none)" option;
  selecting it sets `nameCleared`; the Company/org control offers a non-detected
  source name (e.g. "Round Rock ISD") as a "(from name)" candidate and selecting it
  records `orgChoice`.
- **End-to-end** (`AppFlowTest`): a fixture with a lone "Round Rock ISD"-style card
  paired (manual merge) or a company-only cluster → set name "(none)" + org →
  `VcfExporter().export(final)` contains `ORG:` and **no `FN:`** line for that
  contact.
- `./gradlew check` green (line ≥90 / branch ≥70); Spotless; Konsist (`core`
  UI-free).

## 8. Scope

In scope: `MergeReviewTypes` (`nameCleared`), `MergeReviewStore`
(`clearName`, `chooseName` reset, `companyAutoSuggest`, `commit`), `MergeScreen`
(Name "(none)" + all-names-promotable in `CompanyOrgField`), `CompanyNameDetector`
(ISD + Independent School District) + tests. Out of scope: #37 (standalone
reviewed mark-as-company); the matcher.

## 9. Notes

- A merge where the user clears the name and sets no org yields a card with neither
  name nor org. That is the user's explicit choice; we do not block it (it would
  export as a near-empty card). Phase B's reviewed flow is where guidance for the
  standalone case lives.

# Name + Company (org) Choice — Design Spec (#29 Phase 1)

Date: 2026-06-27
Status: Approved (design); pending implementation plan
Builds on: merge review + redesign (`MergeReviewStore`, `MergeScreen`), manual
merge (4b-2). core matcher/merger/apply engine unchanged.
Tracking issue: #29

## 1. Purpose

During merge the user must be able to choose **both** the person's name **and**
the company/org — and handle the common real-world mess where a **company name is
stored in the name field**. Example: Card A name "Jane Smith" (no org); Card B
name "Acme Inc" (company mis-filed as the person name). They are the same entity;
the desired merge is name = "Jane Smith", org = "Acme Inc" (promoted from B's name
field).

Today the merge detail lets the user pick the person name (a radio →
`chooseName`/`nameChoiceId`, applied as a post-`applyDecisions` override in
`commit()`), and org only appears as a generic conflict radio when ≥2 sources have
*differing* org values. There is no way to (a) see that a name looks like a
company, or (b) promote a mis-filed company name into the merged org.

This is **Phase 1**: UI + store + a pure detector. **Phase 2** (improve the
matcher so these person↔company-name duplicates are actually proposed) is a
separate ADR + spec.

## 2. Decisions

- **Company-name detection is a pure `core` utility** (`CompanyNameDetector`). It
  is NOT a matcher change — the matcher/merger/apply engine is untouched. The
  detector lives in `core` because it is pure, dependency-free domain logic and
  Phase 2's matcher will reuse it. (The issue framed Phase 1 as "no core change";
  this is interpreted as "no matcher change" — adding a pure, separately-tested
  utility file is in keeping with that.)
- **Detection signals (all four enabled):** in precision order —
  `LEGAL_SUFFIX`, `AMPERSAND`, `KEYWORD`, `WEAK`. See §3.
- **All four signals badge** a name as "looks like a company" in the detail pane.
- **Auto-suggest fires on any detected signal (including WEAK)**, gated by *"no
  source card already has an org"*: pre-select the company-like name as the merged
  **org**, and pre-select a *different* card's non-company name as the person
  **name** (when one exists). Always overridable via the radios.
- **Org choice is stored as a UI override** (`orgChoice: String?`) and applied in
  `commit()` after `applyDecisions` and the name override — mirroring the existing
  name-override mechanism. The core engine is not touched.
- **Org leaves the generic conflict loop** and gets a dedicated "Company / org"
  control (title/notes stay generic conflicts).

## 3. Company-name detector (`core`)

New file `core/company/CompanyNameDetector.kt` (pure):

```kotlin
enum class CompanySignal { LEGAL_SUFFIX, AMPERSAND, KEYWORD, WEAK }

object CompanyNameDetector {
    fun detect(name: ContactName): CompanySignal?  // null = not company-like
}
```

It evaluates the name's display string (formatted, else given+family joined),
returning the **highest-precision** matching signal (checked in this order):

1. **`LEGAL_SUFFIX`** — the last token (stripped of trailing `.`) is one of a
   fixed set, case-insensitive: `Inc, LLC, L.L.C., Ltd, Corp, Co, GmbH, PLC, SA,
   S.A., LLP, LP, Group, Holdings, Corporation, Incorporated, Company`.
2. **`AMPERSAND`** — contains `&`, or a whitespace-delimited `and Co` / `& Co`.
3. **`KEYWORD`** — contains (whole-word, case-insensitive) one of a fixed keyword
   set: `Services, Solutions, Restaurant, Plumbing, Salon, Clinic, Studio, Bank,
   Agency, Consulting, Systems, Technologies, Enterprises, Industries`.
4. **`WEAK`** — the whole display string is ALL-CAPS (has letters and equals its
   uppercase form), OR it is a single token (no whitespace) with no family name.

Negatives that must NOT match: ordinary "Jane Smith", a person whose surname is a
dictionary word not in the keyword set, "Dave" only matches `WEAK` (single token)
— acceptable since the user opted into WEAK-driven auto-suggest, and it is gated
by "no source has org" + only pre-selects when another card supplies a real name.

Keyword/suffix lists are small `private` constants in the detector. YAGNI: no
config, no external list, no locale handling.

## 4. State model (`MergeReviewTypes`)

Add one field to `ReviewItem`:

```kotlin
val orgChoice: String? = null,   // chosen merged org; null = engine default
```

(`nameChoiceId` already exists for the person-name override.)

## 5. Store (`MergeReviewStore`)

- **New intent** `chooseOrg(itemId, value: String)` — sets `orgChoice = value`.
  The "(none)" radio passes `""`. (`orgChoice` stays nullable only to mean "user
  has not chosen → use engine default"; once chosen it is non-null, possibly `""`.)
- **Auto-suggest defaults in `buildItems()`** (and in `manualMerge`, since manual
  clusters can also exhibit this): when assembling a `ReviewItem`, if **no member
  has a non-blank org** and **some member's name is company-like** (detector
  non-null), then set `orgChoice` = that member's display name and, if a
  *different* member has a non-company name, set `nameChoiceId` = that member's id.
  If multiple members look like companies, pick the highest-precision (then first
  by existing member order). This is computed once at build; the user can override.
  Extract this into a small private helper `companyAutoSuggest(members)` returning
  `(nameChoiceId?, orgChoice?)` so `buildItems` and `manualMerge` share it.
- **`commit()`**: after the existing name-override step, apply org overrides:
  for each accepted item with a non-null `orgChoice`, set the merged contact's
  `org` to it (empty string → `org = null`). Match the merged contact by
  `proposal.merged.id`, same as the name override.

The engine (`DecisionApplier`, `ContactMerger`) is unchanged.

## 6. Detail-pane UI (`MergeScreen`)

- **Name radio (existing):** for each source name, append a small
  "· looks like a company" tag when `CompanyNameDetector.detect(member.name) != null`.
- **New "Company / org" radio group** (replaces org in the generic conflict loop):
  - Candidates (distinct, in this order): each source card's non-blank `org`;
    then each company-like source **name** not already present as an org, tagged
    "(from name)"; then **"(none)"**.
  - Selected = `item.orgChoice` if set, else the engine default (first non-blank
    source org), else "(none)".
  - Selecting a candidate calls `store.chooseOrg(item.id, value)` ("(none)" passes
    `""`). In `commit()`, `orgChoice == ""` → merged `org = null`; a non-empty
    `orgChoice` → that string; `orgChoice == null` → engine default (unchanged).
- The generic conflict loop in `MergeDetailContent` skips `field == "org"` (it is
  rendered by the dedicated control); `title`/`notes` conflicts unchanged.

## 7. Testing

- **`CompanyNameDetector`** (pure unit table): each signal positive
  ("Acme Inc"→LEGAL_SUFFIX, "Smith & Sons"→AMPERSAND, "Joe's Plumbing"→KEYWORD,
  "ACME"/"Dave"→WEAK) and negatives ("Jane Smith"→null, "Jane Baker"→null since
  "Baker" is not a keyword, "" / blank → null). Highest-precision wins when
  several apply.
- **`MergeReviewStore`:** `chooseOrg` sets/clears `orgChoice`; `commit()` applies
  the org override (and clears to null on empty); auto-suggest sets `orgChoice` +
  `nameChoiceId` for a no-org cluster with a company-like name, and does NOT fire
  when a source already has an org; auto-suggest also works for `manualMerge`.
- **Compose UI:** the "looks like a company" badge renders on a company-like name;
  the Company/org control lists a promotable name and selecting it records the
  choice; "(none)" clears org; org no longer appears in the generic conflict list.
- **End-to-end** (`AppFlowTest`): fixture with Card A "Jane Smith" (no org) + Card
  B "Acme Inc" (no org, same email domain so the user can manual-merge them in
  Phase 1) → manual-merge → accept → exported merged contact has name "Jane Smith"
  and org "Acme Inc". (Phase 1 relies on manual merge to pair them, since the
  matcher linking is Phase 2.)
- Konsist keeps `core` UI-free; coverage floors line ≥90 / branch ≥70 unchanged.

## 8. Scope

In scope: `core/company/CompanyNameDetector.kt` (+ test); `MergeReviewTypes`
(`orgChoice`); `MergeReviewStore` (`chooseOrg`, auto-suggest in
`buildItems`/`manualMerge`, org override in `commit`); `MergeScreen` (name badge,
Company/org control, skip org in generic conflicts); their tests; an e2e fixture +
case. The matcher/merger/apply engine is unchanged. **Out of scope:** Phase 2
matcher linking of person↔company-name duplicates (separate ADR + spec); #30
visual polish; deletion/export screens.

## 9. Known limitations

- Phase 1 does not *find* these duplicates automatically — the user pairs them via
  manual merge (4b-2). Phase 2 adds matcher support.
- `WEAK`-driven auto-suggest can mis-flag a single-token or all-caps personal name
  (e.g. "Dave", "MOM"); it only changes defaults when no source has an org and a
  different card supplies a real name, and is always overridable. Accepted per the
  user's choice to enable all four signals for auto-suggest.

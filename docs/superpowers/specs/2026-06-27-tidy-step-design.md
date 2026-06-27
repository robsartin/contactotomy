# Tidy Step (generalize Companies + email→name) — Design Spec (#54)

Date: 2026-06-27
Status: Approved (design); pending implementation plan
Builds on: #37 (Companies step), #44 (its detail pane), #29/#36 (CompanyNameDetector, name/org clearing).
Tracking issue: #54

## 1. Purpose

The post-merge **Companies** step currently reviews one normalization — marking a
card as a company (name → org). Broaden it into a general **Tidy** step that
reviews per-card normalizations, and add a second one the user asked for:
**use a card's email as its name** when it has an email but no name and no company
(otherwise such cards are nameless and unusable). Both are reviewed defaults
(pre-checked, untickable), non-destructive, and applied **after** matching so they
never disturb duplicate detection.

## 2. Decisions

- **Two disjoint suggestion types**, each pre-checked by default, each a tickable
  row in the Tidy list:
  1. **name → company** (existing): `CompanyNameDetector.isHighPrecision(name)` and
     no org → set org from the name, clear the name (`markAsCompany`).
  2. **email → name** (new): no name (`companyNameText(name)` blank) **and** no org
     **and** ≥1 email → set name = first email (`nameFromEmail`).
  A card matches at most one (type 1 requires a name; type 2 requires none).
- **email → name is a reviewed default** (pre-checked in the Tidy step), not silent;
  applied post-merge; non-destructive (the email remains an email).
- **Rename "Companies" → "Tidy":** `Screen.COMPANIES`→`Screen.TIDY`,
  `CompanyScreen`→`TidyScreen`, `CompanyReviewStore`→`TidyStore`,
  `AppState.companyContacts`→`tidyContacts`, `setCompanyContacts`→`setTidyContacts`,
  step label "Companies"→"Tidy". `CompanyNameDetector` keeps its name (it's company
  detection). The normalizer gains `nameFromEmail` alongside `markAsCompany`.
- **No matcher/merger/exporter change.**

## 3. Core (`core/company/`)

- `CompanyNormalizer` gains:
  ```kotlin
  fun nameFromEmail(contact: Contact): Contact =
      contact.copy(name = ContactName(formatted = contact.emails.first()))
  ```
  (Caller guarantees `emails` is non-empty.) Keeps `markAsCompany` unchanged.
  `companyNameText` and `isHighPrecision` unchanged.

## 4. Store (`ui/TidyStore`, renamed from `CompanyReviewStore`)

- A per-card classification:
  ```kotlin
  enum class TidyAction { COMPANY, EMAIL_NAME }
  fun actionFor(contact): TidyAction =
      if (companyNameText(contact.name).isBlank() && contact.org.isNullOrBlank() && contact.emails.isNotEmpty())
          TidyAction.EMAIL_NAME else TidyAction.COMPANY
  ```
  (Used for the default-mark, the preview, and the commit transform. A marked card
  with neither a company-ish name nor the email-name shape still falls to COMPANY —
  preserving #37's "mark any card as a company" manual override.)
- `listed()` = contacts worth tidying: `companyNameText(name).isNotBlank()` (a name
  to mark as company) **or** (name blank && `emails.isNotEmpty()`) (an email→name
  candidate). Truly empty cards are omitted.
- `markedIds` initialized to the **suggested** cards:
  `isHighPrecision(name)` **or** (name blank && org blank && emails non-empty).
- `toggle(id)` unchanged.
- `commit()`: for each marked id, apply `when (actionFor(c)) { EMAIL_NAME ->
  CompanyNormalizer.nameFromEmail(c); COMPANY -> CompanyNormalizer.markAsCompany(c) }`;
  others pass through.
- `TidyState(markedIds)` (renamed `CompanyReviewState`).

## 5. Screen (`ui/TidyScreen`, renamed from `CompanyScreen`)

Keeps the #44 layout (left list + right detail). Per row:
- a `Checkbox` (mark) with `testTag("mark:<id>")`; row body click selects for the
  detail pane (unchanged).
- name (or "(no name)") + `SourceBadge`.
- when marked, a preview hint from `actionFor`:
  - `COMPANY` → "→ org: ${companyNameText(name)}"
  - `EMAIL_NAME` → "→ name: ${first email}"
- Right detail pane shows the selected card (name/phones/emails/org/source) — as in #44.
- Filter `TextField` + "K of N marked" summary preserved.

## 6. Wizard wiring

- `Screen.TIDY` replaces `COMPANIES` (same position, between MERGE and DELETION).
- `AppState.tidyContacts`/`setTidyContacts`; `workingContacts = finalContacts ?:
  tidyContacts ?: mergedContacts ?: contacts`; deletion source =
  `tidyContacts ?: mergedContacts ?: contacts`.
- `App.kt` hoists `TidyStore(mergedContacts ?: contacts)`, renders `TidyScreen`,
  the Next on `TIDY` commits via `setTidyContacts(tidyStore.commit())`. StepIndicator
  label "Tidy".

## 7. Testing

- **`CompanyNormalizer`**: `nameFromEmail` sets `name.formatted` = first email (and
  leaves emails intact); `markAsCompany` unchanged.
- **`TidyStore`** (renamed tests): COMPANY suspects pre-marked (existing); an
  email→name card (no name, no org, ≥1 email) pre-marked with `actionFor == EMAIL_NAME`;
  `commit` applies the right transform per card (company → org; email→name → name);
  a manually-toggled plain card → COMPANY (markAsCompany); `listed()` includes
  email→name candidates and omits empty cards.
- **`TidyScreen`** (renamed): an email→name row shows "→ name: <email>" when marked;
  a company row shows "→ org: …"; detail pane + filter + toggle still work.
- **End-to-end** (`AppFlowTest`): a nameless card with an email flows
  Import→Merge→**Tidy (email→name pre-checked)**→Deletion→Export → exported card has
  `FN:<email>`. Existing flow/nav tests updated for the rename (COMPANIES→TIDY).
- `./gradlew check` green (line ≥90 / branch ≥70); Konsist core UI-free.

## 8. Scope

In scope: `CompanyNormalizer` (`nameFromEmail`); rename Companies→Tidy across
`AppState`/`AppStore`/`App.kt`/screen/store + tests; the email→name suggestion +
commit transform; e2e. Out of scope: matcher/merger/exporter; the starter-rule trim
(separate, #53); #52 (delete cluster).

## 9. Notes

- The rename is mechanical but wide; the implementation plan isolates it as one task
  so the email→name behavior change is reviewable on its own.
- A nameless card with an email but the user unticks it → stays nameless (passes
  through unchanged), as today.

# Review: smarter defaults & auto-cleanup — Design Spec (#80)

Date: 2026-07-01
Status: Approved (autonomous)
Tracking issue: #80 (part of the unified Review step #78)

## 1. Purpose

Concrete cleanup wins in the Review step: (A) generalize the no-name fallback to use
a **phone** when there's no email; (B) let the user **mark any merged card as a
company** from the Review detail; (C) **auto-dedupe** emails and addresses on the
merged card.

## 2. Scope

- A: no-name → email **or phone** as name (generalizes the old Tidy email→name).
- B: "Mark as company" action in the Review merge-result detail (Section 1),
  reusing the #79 override layer (no new model).
- C: dedupe emails (case-insensitive) and addresses (by display string) on merges.
- **Out of scope / noted:** deeper "auto accept/reject from data combinations"
  heuristics — the field auto-selection already prefers the most-complete value via
  `ContactMerger`; changing default accept/reject decisions is deferred (too
  behavior-changing for this pass). This issue delivers A/B/C, the user's concrete asks.

## 3. A — no-name → email or phone

- **core** `CompanyNormalizer` gains:
  ```kotlin
  fun nameFromPhone(contact: Contact): Contact =
      contact.copy(name = ContactName(formatted = contact.phones.first()))
  ```
  (Caller guarantees `phones` non-empty. `nameFromEmail` unchanged.)
- **`ui/ReviewStore`** Section 2:
  - `enum TidyAction { COMPANY, EMAIL_NAME, PHONE_NAME }`.
  - `suggested(c)` now also true when name blank && org blank && emails empty &&
    phones non-empty (a nameless phone-only card).
  - `actionFor(c)`: if name blank && org blank → `EMAIL_NAME` when it has emails, else
    `PHONE_NAME` when it has phones; otherwise `COMPANY`.
  - `commit()` dispatches `PHONE_NAME -> CompanyNormalizer.nameFromPhone(c)`.
  - The Section-2 hint in `ReviewScreen` shows `→ name: <first phone>` for `PHONE_NAME`.

## 4. B — mark a merged card as a company (Review detail)

- In the merge-result detail (`MergeScreen` detail content, reused by Review Section 1)
  add a **"Mark as company"** button (testTag `mark-as-company`). On click:
  - `store.setOrgOverride(item.id, companyNameText(store.effectiveName(item)))` (use the
    company text of the effective name; if blank, fall back to the effective name's
    display string), and
  - `store.setNameOverride(item.id, ContactName())` (clear the name).
  This reuses the #79 override intents — no new model. Result on commit: org set from
  the (former) name, name cleared — i.e. a company card. `companyNameText` is in
  `core/company`.
- Keep it always available (the user can mark any merged card as a company).

## 5. C — auto-dedupe emails & addresses

In `core/merger/ContactMerger`, when unioning the merged card's fields:
- **emails:** distinct **case-insensitively**, preserving the first occurrence's
  casing (emails are already normalized on import, so this is belt-and-suspenders for
  any variant that slips through).
- **addresses:** distinct by `PostalAddress.toDisplayString()` (data-class equality
  already dedupes exact structural duplicates; this also collapses structurally-
  different-but-textually-identical addresses). Preserve first occurrence + order.
Keep all other union behavior unchanged.

## 6. Testing

- **core**: `nameFromPhone` sets `name.formatted` = first phone (emails/phones intact);
  `ContactMerger` dedupes case-variant emails (`A@x.com` + `a@x.com` → one) and
  duplicate addresses across members.
- **`ReviewStore`**: a nameless phone-only card is a suggested clean candidate with
  `actionFor == PHONE_NAME`; `commit()` names it from its phone; email-bearing nameless
  cards still map to `EMAIL_NAME` (regression); company cards unchanged.
- **`MergeScreen`/`ReviewScreen` UI**: the "Mark as company" button on a selected
  merged item clears the name and sets org to the company text; `commit()` reflects it;
  the Section-2 hint shows `→ name: <phone>` for a phone-name card.
- `./gradlew check` green: line ≥90 / branch ≥70 (cover new branches), Spotless/ktlint,
  Konsist (core stays UI-free; `nameFromPhone`/dedupe are in `core`).

## 7. Notes
- B adds no model — it composes the #79 override intents. A adds one small `core`
  function + Section-2 branch. C is a localized `ContactMerger` tweak.
- YAGNI: no deeper accept/reject heuristics; no address/URL freeform add (that was #79's
  boundary).

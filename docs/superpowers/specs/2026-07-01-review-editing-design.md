# Review: full card editing (name components, org, notes, freeform) — Design Spec (#79)

Date: 2026-07-01
Status: Approved (design)
Tracking issue: #79 (part of the unified Review step #78)

## 1. Purpose

Let the user directly edit the **merged-result card** in Review **Section 1
(Duplicates to merge)** — edit name components, org, and notes as freeform text, and
add freeform phone/email values — rather than only picking from source values. Layers
on top of the existing per-field pick/exclude model; an explicit edit wins.

## 2. Scope

- In scope: editing on the **merge-result detail pane** (the `MergeScreen` detail
  content reused by `ReviewScreen` Section 1). Fields: name components
  (prefix/given/middle/family/suffix), org, notes (+ append source notes), and
  freeform-add phone/email.
- Out of scope (later): free-editing arbitrary Section-2 singletons; smarter
  auto-defaults/dedupe (#80); add freeform address/URL (can follow); keyboard (#82).
- No `core` change; all in `ui` (`MergeReviewStore` + `MergeScreen`).

## 3. Model — `MergeReviewStore` per-item overrides

Add nullable override fields to `ReviewItem` (inspect the current data class; add
alongside `nameChoiceId`/`orgChoice`/`excludedValues`/`conflictChoices`):

- `nameOverride: ContactName?` — when non-null, the merged card's name is exactly this
  (supersedes `nameChoiceId`/`nameCleared`).
- `orgOverride: String?` — when non-null, merged org = `orgOverride.ifBlank { null }`
  (supersedes `orgChoice`).
- `notesOverride: String?` — when non-null, merged notes = `notesOverride.ifBlank { null }`.
- `addedPhones: List<String>` and `addedEmails: List<String>` (default empty) —
  freeform values appended to the merged card's phones/emails.

Intents (mirror the existing intent style, updating the item by id):
- `setNameOverride(itemId, ContactName?)` — or granular
  `setNameComponent(itemId, component, value)` that builds/updates the override
  `ContactName`. Granular is friendlier for five text fields; use it and keep an
  internal `nameOverride` assembled from the components (seeded from the current
  effective name on first edit).
- `setOrgOverride(itemId, String?)`, `setNotesOverride(itemId, String?)`.
- `appendSourceNotes(itemId)` — sets `notesOverride` to the newline-joined non-blank
  notes of all cluster members (deduped, order-preserved).
- `addPhone(itemId, String)`, `removeAddedPhone(itemId, String)`,
  `addEmail(itemId, String)`, `removeAddedEmail(itemId, String)`.

## 4. Commit — apply overrides last

In `commit()` (and/or the applier), after the existing logic builds the merged
`Contact` for an accepted item, apply overrides on top:

```
var c = <existing merged contact>
item.nameOverride?.let { c = c.copy(name = it) }
item.orgOverride?.let { c = c.copy(org = it.ifBlank { null }) }
item.notesOverride?.let { c = c.copy(notes = it.ifBlank { null }) }
if (item.addedPhones.isNotEmpty()) c = c.copy(phones = (c.phones + item.addedPhones).distinct())
if (item.addedEmails.isNotEmpty()) c = c.copy(emails = (c.emails + item.addedEmails).distinct())
```

Precedence: override wins over the pick/exclude/choice results. Non-overridden fields
keep today's behavior exactly (so existing tests still pass).

## 5. UI — merge-result detail pane (`MergeScreen` detail content)

Add an editable block to the merged-result preview (reused in Review Section 1):
- **Name:** five `OutlinedTextField`s labeled Prefix / Given / Middle / Family /
  Suffix, pre-filled with the current effective name components. Editing any calls
  `setNameComponent`. testTags `name-prefix`/`name-given`/`name-middle`/`name-family`/`name-suffix`.
- **Org:** an `OutlinedTextField` pre-filled with the effective org; testTag `org-edit`.
- **Notes:** a multiline `OutlinedTextField` pre-filled with the effective notes +
  an **"Append source notes"** button (testTag `append-notes`); field testTag `notes-edit`.
- **Add phone / add email:** a small input + "Add" button each (testTags
  `add-phone-input`/`add-phone-btn`, `add-email-input`/`add-email-btn`); render added
  values as removable chips/rows (reuse the existing pill/`ValuePill` style if handy).
- Keep the existing name/org choice controls; they still set `nameChoiceId`/`orgChoice`
  and act as quick-fill defaults when no override is set. An override, once typed,
  governs.

Pre-fill logic: seed the editable fields from the item's current *effective* values
(the value commit would currently produce). Only set an override when the user
actually edits (so untouched items behave exactly as before).

## 6. Testing

- **`MergeReviewStore`**: `setNameComponent` builds a `nameOverride` and `commit()`
  yields that name (overriding a set `nameChoiceId`); `setOrgOverride` (incl. clearing
  to blank → null org); `setNotesOverride`; `appendSourceNotes` joins members' notes;
  `addPhone`/`addEmail` appear in the committed card and dedupe; removing an added
  value drops it; an item with NO overrides commits identically to today (regression).
- **`MergeScreen`/`ReviewScreen` UI**: the editable fields render pre-filled for a
  selected merged item; typing updates state; "Append source notes" fills the notes
  field; adding a phone/email shows a chip and it survives commit.
- `./gradlew check` green: line ≥90 / branch ≥70 (cover new branches), Spotless/ktlint,
  Konsist (no `core` change).

## 7. Notes

- Keep the diff additive: don't disturb the existing pick/exclude/conflict logic —
  overrides are a new layer applied last.
- YAGNI: phone/email freeform add only (not address/URL yet); single merged card
  editing (not arbitrary singletons).

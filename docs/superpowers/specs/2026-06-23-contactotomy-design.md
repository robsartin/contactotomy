# Contactotomy — Design Spec

Date: 2026-06-23
Status: Approved (design); pending implementation plan

## 1. Purpose

Contactotomy is a local, offline macOS desktop app that helps consolidate and
clean up a contact list spread across Apple Contacts and Google Contacts, plus
accumulated junk from old imports and email addresses. It:

- Imports vCard (`.vcf`) exports from Apple and Google.
- Detects duplicate cards for the same person and proposes merges with a visual
  side-by-side preview and per-field control.
- Flags cards for deletion via reusable, combinable rules.
- Exports one cleaned vCard the user mirrors back into both accounts.

**Success metric:** merge the user's Google and Apple contacts and remove ~20% of
redundant cards in a sitting. The tool is **re-run** as cruft re-accumulates.

**Scope:** built for a single local user. Contact data never leaves the machine.
Notes on broadening to multiple users appear in §12.

The consequential decisions below are recorded as ADRs in `docs/adr/`; this spec
is the narrative those ADRs are distilled from.

## 2. Constraints & principles

- **Local & offline.** No network, no account APIs, no telemetry (ADR-0003).
- **Non-destructive to inputs.** The app never modifies source files; the user
  keeps backups (documented).
- **Safe by default.** Only high-confidence duplicates auto-merge; everything
  else, and every deletion, is review-gated.
- **TDD throughout.** Red → green → refactor → commit; the failing test runs
  before the implementation exists. Pure-logic modules are fully unit-testable
  headless.
- **PR-based workflow.** Issue → branch → TDD commits → PR to main → squash;
  never commit directly to main.

## 3. Tech stack (ADR-0002)

- **Kotlin + Compose Desktop** macOS app.
- **ez-vcard** (BSD) — vCard parse/serialize.
- **libphonenumber** (Apache 2.0) — phone normalization to E.164 + fuzzy compare.
- **Gradle** build; **JUnit** tests; **Konsist** for architecture enforcement
  (ADR-0006).

Complete third-party surface; all offline, no telemetry.

## 4. Architecture & modules

A pure **core engine** (no Compose dependency, fully unit-testable) separated from
a thin **Compose UI**. The boundary is enforced by Konsist (ADR-0006).

1. **`importer`** — reads one or more `.vcf` files, tags each card with its source
   (Apple / Google / filename), preserves the raw vCard. Uses ez-vcard.
2. **`model`** — the normalized internal contact representation (§5).
3. **`matcher`** — clusters cards into "same person" groups with a confidence tier
   (§6).
4. **`merger`** — computes the proposed merged card per cluster, with field-level
   provenance and conflicts (§7).
5. **`rules`** — the deletion-rule engine (§8).
6. **`review`** — UI-facing state that tracks accept/reject/field-toggle decisions
   for merge and deletion review.
7. **`exporter`** — writes the cleaned vCard(s) for re-import (§9).
8. **`ui`** — Compose Desktop screens, including the visual merge preview (§10).
9. **`docs`** — the rendered export → clean → import instructions (§9).

Modules 1–7 carry no Compose dependency.

## 5. Data model

Normalized contact:

- `id` (app-internal), `source` (Apple / Google / file), `rawVCard` (verbatim,
  for export fidelity and as a safety net).
- `names`: full plus parsed components (prefix / given / middle / family /
  suffix) — required for dropped-middle comparison.
- `phones`: each normalized to E.164 via libphonenumber (default region US/`+1`,
  configurable); original string retained.
- `emails`: lowercased, trimmed.
- `addresses`, `org`, `title`, `urls`, `notes`.
- `categories`: Google labels via vCard `CATEGORIES` (preserved through merge and
  export).
- `createdAt` / `modifiedAt`: best-effort (Apple `REV`, Google export metadata);
  null if absent.

Multi-value fields (phones, emails, addresses, urls, categories) retain all
values.

## 6. Matching (ADR-0005)

Pairwise signals grouped into clusters; **name-gated**:

- A **shared phone or email is corroborating, not deciding** — high confidence
  only when the **name is also compatible** (dropped/abbreviated middle; common
  nickname pairs like Bob↔Robert; case/punctuation-insensitive).
- **Different given names override shared contact info** — never auto-merged
  (e.g., a married couple sharing one phone stays two cards).
- Email weighted slightly stronger than phone for identity; same name gate.

Confidence tiers:

- **High / auto-merge:** compatible name AND (shared phone OR shared email).
- **Uncertain / manual review:** name-only, or ambiguous.
- **Never merged:** shared phone/email with clearly different given names.

Thresholds are tunable constants (surfaced in settings later). Phones normalized
to E.164 before comparison.

## 7. Merging (ADR-0005)

- **Multi-value fields** (phones, emails, addresses, urls, categories): **union**,
  de-duplicated — nothing dropped.
- **Single-value conflicts** (e.g., two `org`/`title`): **prefer newest** by
  `modifiedAt`; if dates missing/equal, newest-source wins the primary slot and
  the loser is demoted, not discarded.
- Records **field-level provenance** (source of each value) and a **conflict list**
  — the data behind the preview's field toggles.
- "Ask non-intrusively": auto-resolve everything confidently resolvable; only
  genuine conflicts surface as inline toggles, never a blocking modal.

## 8. Deletion-rule engine (ADR-0007)

- **Shell-style wildcards** (`*`, `?`), **case-insensitive**, over email / name /
  org / address / url / notes — e.g. `*@indeed.com`, `sartin@*`.
- **Phone patterns** with `?` digit slots over normalized numbers — `512-???-????`.
- **Structural predicates** — `no name AND no phone`, `no email`, `empty card`,
  `created before <date>`, `source = …`. `never contacted` is **stubbed**
  (skipped) pending future usage signals (§11).
- **AND/OR composition** with nestable groups, e.g.
  `(*@indeed.com) OR (created before 2015 AND never contacted)`.
- Rules are **named, saved** to a human-editable local file (e.g. JSON/TOML),
  **reusable** across runs; ships with **starter rules** from the user's examples
  (`*@indeed.com`, `sartin@*`, `512-???-????`, "no name and no phone").
- Rules run on the **post-merge** set by default; always produce a **review list**
  with each hit annotated by the matching rule/condition. Nothing deletes without
  explicit batch confirmation.

## 9. Export & the end-to-end workflow (ADR-0003, ADR-0004)

**Output:** one canonical cleaned **vCard 3.0**, UTF-8, carrying through
unmodeled fields from `rawVCard` so nothing silently drops. Default is a single
combined file (per-source split is a future option).

**Documented process** (rendered in-app and in the repo):

1. **Export** — Apple: Contacts.app → select all → File → Export → Export
   vCard… → `apple.vcf`. Google: contacts.google.com → Export → vCard →
   `google.vcf`.
2. **Back up** — save copies of both exports; the app also never mutates inputs.
3. **Clean** — load both into Contactotomy → review merges → run deletion rules →
   review → export `contacts-clean.vcf`.
4. **Wipe** (required by mirror-into-both) — after confirming backups, delete all
   existing contacts in **each** account. Prominent "back up first" warnings.
5. **Import** — import `contacts-clean.vcf` into **both** Apple and Google.
6. **Re-runs** — repeat from step 1 as needed.

**Documented tradeoffs of wipe + re-import:** `createdAt` resets and **Apple group
membership is lost** (Apple's vCard export omits it; not recoverable from the
file). **Google labels survive** via `CATEGORIES`. A future optional live-Contacts
reader could preserve Apple groups (§11).

## 10. UI (Compose Desktop)

Three keyboard-drivable screens with bulk actions.

**Merge review (centerpiece):**

- A **primary card + stacked secondaries** "before" panel (chosen over horizontal
  scroll for readability at scale), color-coded by source, each showing
  `modifiedAt`.
- A **"merged result"** panel where every value is an include/exclude chip;
  single-value conflicts show both with prefer-newest pre-selected and a one-click
  switch; excluded values gray out live.
- **Provenance** shown inline/on hover.
- Per-cluster: **Accept merge / Reject (keep separate) / Skip**. High-confidence
  auto-merges flow through a fast "skim and confirm" lane; uncertain clusters
  require an explicit decision.

**Deletion review:**

- Flagged list; selecting a card shows the **full card + the rule/condition that
  flagged it**.
- **Bulk actions:** select-all, approve-by-rule, keyboard approve/reject, filter
  by rule. Batch confirmation before anything is removed.

**Import/Export & instructions:** file pickers, run/export, and the rendered
Apple + Google export → backup → wipe → import guide.

**Flow:** Import → Match & Merge proposals → Merge review → Rules on post-merge
set → Deletion review → Export. Any stage is re-enterable.

## 11. Testing & architecture enforcement

- **TDD** for modules 1–7; UI kept thin.
- A **fixtures library of messy real-world vCards**: dropped middles,
  shared-phone couples (the locked-in anti-merge case), `*@indeed.com` junk,
  missing names, Apple vs Google format quirks, unicode, multi-value fields.
- **Konsist** architecture tests in CI enforce `core.* !-> ui.*`/Compose and
  naming/package conventions (ADR-0006).

## 12. Future hooks (designed-for, not built)

- **Usage signals** — recency/frequency from Call history, Messages, Mail feeding
  `never contacted`-style predicates and informing merge/keep decisions. The
  matcher and rules leave explicit stubs. (A "dream" feature, not MVP.)
- **Apple-groups preservation** via an optional live-Contacts (Contacts
  framework) reader, replacing the file import on the Apple side.
- **Advanced regex** matching toggle; **per-source split** export.

## 13. Going broader than a single local user (notes, not built)

Today: one local user, files + in-memory, no auth, no network. To serve others
you would add account/identity, a storage backend, multi-tenant isolation, and a
real privacy/security review before any contact data touches a server. Captured
here so the single-user assumptions are visible and revisitable.

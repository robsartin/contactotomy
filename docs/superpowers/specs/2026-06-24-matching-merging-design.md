# Matching & Merging Engine — Design Spec

Date: 2026-06-24
Status: Approved (design); pending implementation plan
Builds on: `2026-06-23-contactotomy-design.md` (§6 Matching, §7 Merging) and ADR-0005.

## 1. Purpose

Provide the pure-Kotlin core engine that turns a flat list of normalized
`Contact`s (from Plan 1's importer) into:

1. **Auto-merge clusters** — groups of cards that are confidently the same person.
2. **Uncertain pairs** — likely-but-not-certain matches surfaced for manual review.
3. **Merge proposals** — for each cluster, a computed merged card with field-level
   provenance and conflicts.
4. **Apply** — a pure function that turns the user's accept/reject/field decisions
   into the final deduplicated contact list.

No UI, no persistence, no deletion rules. All logic lives under
`com.robsartin.contactotomy.core` and must not import UI/Compose (Konsist-enforced,
ADR-0006).

## 2. Key decisions (this plan)

- **Clustering: transitive on strong links only.** Cards chain into one cluster
  only through HIGH-confidence edges; UNCERTAIN links never pull a cluster
  together.
- **Surname changes match, gated by shared contact info.** Same given name +
  shared phone/email but a different family name (maiden↔married) is a HIGH edge,
  flagged `SURNAME_CHANGE`.
- **Scope: proposals + apply.** The engine both proposes merges and applies
  decisions to produce a final list.
- **Determinism is a requirement.** Same input cards → same clusters, proposals,
  orderings, and conflict pre-selections, so the engine is cleanly unit-testable.

## 3. Modules

Three new packages under `com.robsartin.contactotomy.core`:

- **`matcher`** — name compatibility, candidate generation (blocking), edge
  classification, clustering.
- **`merger`** — builds a `MergeProposal` per cluster.
- **`apply`** — applies `MergeDecision`s to produce the final contact list.

## 4. Core types

```
enum class Confidence { HIGH, UNCERTAIN }   // no edge at all == "never merge"

enum class MatchReason {
    SHARED_PHONE, SHARED_EMAIL,
    NAME_EXACT, NAME_NICKNAME, NAME_DROPPED_MIDDLE, NAME_INITIAL,
    SURNAME_CHANGE, NAME_ONLY,
}

data class MatchEdge(
    val a: Contact, val b: Contact,
    val confidence: Confidence,
    val reasons: List<MatchReason>,
)

data class Cluster(                          // size >= 2; singletons are not clusters
    val id: String,                          // deterministic, derived from members
    val members: List<Contact>,
    val confidence: Confidence,              // HIGH for auto-merge clusters
    val reasons: List<MatchReason>,
)

data class MatchResult(
    val clusters: List<Cluster>,             // auto-merge candidates (HIGH edges)
    val uncertainPairs: List<MatchEdge>,     // UNCERTAIN edges, for manual review
)

data class FieldProvenance(
    val field: String,
    val value: String,
    val sourceContactIds: List<String>,
)

data class ConflictCandidate(val value: String, val sourceContactId: String, val modifiedAt: java.time.Instant?)
data class FieldConflict(val field: String, val candidates: List<ConflictCandidate>, val chosen: String)

data class MergeProposal(
    val cluster: Cluster,
    val merged: Contact,
    val provenance: List<FieldProvenance>,
    val conflicts: List<FieldConflict>,
)
```

## 5. Matching (`matcher`)

### 5.1 Name compatibility (independently testable helpers)

- **Given-name compatible** if any of: equal (case/punctuation-insensitive);
  nickname-equivalent (e.g. Bob↔Robert) via a bundled curated list; initial-
  compatible (`R.` ↔ `Robert`, same first letter where one side is an initial);
  or equal after dropping the middle name.
- **Family-name compatible** if: equal, or one side is missing. A *different*
  family name is acceptable only as a SURNAME_CHANGE (see edge rules), never on
  its own.

The nickname dataset is a small, public-domain, editable resource file bundled
with the app (expandable later).

### 5.2 Edge classification (encodes ADR-0005 + this plan's decisions)

For a candidate pair:

1. **Given names not compatible → no edge.** Overrides everything, including a
   shared phone/email (the married-couple-sharing-a-number case).
2. Given compatible AND (shared phone OR shared email):
   - family compatible → **HIGH** (reasons include SHARED_*, NAME_*)
   - family different → **HIGH**, with **SURNAME_CHANGE** flagged
3. Given compatible AND family compatible AND no shared contact info →
   **UNCERTAIN** (NAME_ONLY).
4. Shared phone/email but given incompatible → **no edge**.
5. Shared phone/email AND given **indeterminate** (one side has no given name, so
   there is neither a positive match nor a conflict) → **UNCERTAIN** (NAME_ONLY).
   A missing given name is never treated as a conflict.

Email is weighted no weaker than phone for identity, but the name gate above
applies equally to both.

### 5.3 Candidate generation (blocking — performance at thousands of cards)

Build inverted indexes: `phone(E.164) → cards`, `email → cards`,
`familyNameKey → cards`. Candidate pairs are only those sharing at least one block
key; each candidate is then classified per 5.2. This avoids full O(n²)
comparison. `familyNameKey` (normalized family name) yields the name-only
candidates needed for UNCERTAIN edges.

### 5.4 Clustering

Union-find over **HIGH edges only** → connected components of size ≥2 are
auto-merge clusters. UNCERTAIN edges are emitted in `uncertainPairs` and never
merged into a cluster. This is "transitive on strong links only." Cluster ids and
member ordering are deterministic (members sorted by contact id).

## 6. Merging (`merger`)

For each cluster, compute one `MergeProposal`:

- **Primary card:** newest by `modifiedAt`; ties / missing dates broken by sorted
  contact id (deterministic).
- **Multi-value fields** (phones, emails, addresses, urls, categories): union
  across members, de-duplicated (phones on E.164), primary's values first then
  the rest in deterministic order. This combines Google labels from multiple
  cards.
- **Single-value fields** (org, title, notes): newest non-null value; when members
  disagree, record a `FieldConflict` with every candidate (value, source,
  modifiedAt) and pre-select the newest.
- **Structured name:** choose the **most complete compatible name** among members
  (prefer a real middle/given over an initial), not strictly newest — "Robert A
  Sartin" beats a newer "Rob S.".
- **Provenance:** every merged value maps to its source contact id(s).
- **Reversibility & safety net:** the proposal retains the full member list and
  members' `rawVCard`. The merged `Contact` gets a deterministic new id derived
  from its members (this also resolves Plan 1's positional-id caveat — the engine
  does not depend on durable contact ids).

## 7. Apply (`apply`)

```
data class MergeDecision(
    val clusterId: String,
    val action: Action,                      // ACCEPT or REJECT
    val excludedValues: Set<ExcludedValue> = emptySet(),   // field toggles turned off
    val conflictChoices: Map<String, String> = emptyMap(), // field -> overriding value
)
data class ExcludedValue(val field: String, val value: String)  // a value to drop from the merged card
enum class Action { ACCEPT, REJECT }

fun applyDecisions(
    allContacts: List<Contact>,
    proposals: List<MergeProposal>,
    decisions: List<MergeDecision>,
): List<Contact>
```

- **ACCEPT** → the merged card (adjusted by `excludedValues` and
  `conflictChoices`) replaces its members.
- **REJECT** → members pass through unchanged.
- **No decision for a cluster → REJECT** (nothing merges without an explicit yes).
- Singletons (cards in no cluster) always pass through unchanged.
- Output ordering is deterministic. The function holds no UI state; Plan 4's UI
  calls it.

## 8. Testing (TDD, pure/headless)

Fixtures-driven, covering the tricky cases explicitly:

- shared-phone married couple → **no merge**
- nickname (Bob/Robert) + dropped-middle + initial → HIGH
- surname change with shared email → HIGH, SURNAME_CHANGE flagged
- transitive chain A–B–C over HIGH edges → one cluster; a chain connected only by
  an UNCERTAIN edge → **not** clustered
- name-only pair → UNCERTAIN (review), not auto-merged
- merge proposal: union of phones/emails/categories; conflict on differing `org`;
  most-complete name chosen
- apply: accept with a field excluded; reject leaves members intact; determinism
  (same input → same output)

Konsist boundary test continues to enforce no UI/Compose imports in `core`.

## 9. Scope boundary

In scope: `matcher`, `merger`, `apply` — pure logic, fully unit-tested.
Out of scope: deletion-rule engine (Plan 3); Compose UI and review state (Plan 4);
persistence of decisions; usage signals (documented future hook).

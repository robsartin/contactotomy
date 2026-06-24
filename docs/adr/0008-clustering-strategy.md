# 8. Clustering: transitive on strong links only; surname changes gated

Date: 2026-06-24

## Status

Accepted

## Context

Pairwise matches must be grouped into per-person clusters for merging. Entity
resolution risks "merge creep": if A matches B and B matches C, transitively
chaining them can pull together people who never directly matched. The user is
specifically wary of wrongly merging distinct people (e.g., a married couple
sharing a phone). Separately, people who change surnames (maiden↔married) should
still be recognized as the same person. This ADR extends ADR-0005 with the
clustering and surname decisions for the matching/merging engine.

## Decision

- **Transitive on strong links only.** Build clusters with union-find over
  **HIGH-confidence edges only** (compatible name + shared phone/email).
  UNCERTAIN edges (e.g., name-only matches) are surfaced as review pairs and never
  pull a cluster together. Chaining A–B–C is allowed only when every link is HIGH.
- **Surname changes match, gated by shared contact info.** Same given name (or a
  nickname/initial variant) + shared phone or email but a *different* family name
  is a HIGH edge, flagged `SURNAME_CHANGE` so the UI can highlight it. A different
  family name alone (no shared contact info) is never a match.
- **Engine delivers proposals and apply.** The engine both produces merge
  proposals and applies the user's accept/reject/field decisions to produce the
  final list; clusters with no decision default to REJECT.

## Consequences

- Limits merge creep: a single weak edge cannot fuse unrelated people.
- Recognizes surname changes without opening the door to matching unrelated
  same-given-name people, because the shared-contact-info gate is required.
- Some true duplicates with only weak (name-only) similarity fall to manual
  review rather than auto-merge — the safe failure direction, consistent with the
  app's caution.
- Determinism (stable cluster ids, ordering, conflict pre-selection) is required
  so the engine is fully unit-testable.

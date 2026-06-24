# 11. Deletion-rule engine: JSON AST with NOT, national-number phone matching

Date: 2026-06-24

## Status

Accepted

## Context

ADR-0007 established the deletion-rule engine at a high level: shell-style
wildcards, phone patterns, structural predicates, AND/OR composition, saved
reusable rules, and review-gated deletion. Implementing it (Plan 3) requires a few
concrete decisions ADR-0007 left open.

## Decision

- **Rules are an AST serialized to JSON** via `kotlinx.serialization` (adds the
  `kotlinx-serialization-json` dependency and the Kotlin serialization plugin).
  The `Condition` hierarchy is polymorphic with stable discriminators (`text`,
  `phone`, `predicate`, `and`, `or`, `not`), making saved rules self-describing
  and hand-editable.
- **Operators are AND / OR / NOT**, nestable — a mild extension of ADR-0007's
  AND/OR. Negation enables carve-outs (e.g. "no email AND NOT category Family").
- **Phone patterns match the national significant number** (via libphonenumber),
  so `512-???-????` flags any 512 area-code number regardless of stored format;
  unparseable phones fall back to E.164-suffix matching.
- **Cautious predicate defaults:** `CREATED_BEFORE` does not flag when `createdAt`
  is null (dates are best-effort); `NEVER_CONTACTED` is a stub that always
  evaluates false until usage signals exist.
- **Engine and apply are separate pure functions:** `RuleEngine.evaluate` returns
  flagged contacts with per-rule reasons; `applyDeletions` removes only approved
  ids. Nothing deletes without explicit approval (ADR-0007).

## Consequences

- Saved rules are durable, diff-friendly, and editable by hand.
- NOT broadens expressiveness at negligible cost.
- Phone matching is format-independent and intuitive (area-code patterns work).
- The cautious defaults bias toward keeping data rather than over-flagging.
- One new first-party dependency (kotlinx.serialization); acceptable for the
  serialization it provides.

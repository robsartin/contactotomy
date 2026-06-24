# 1. Record architecture decisions

Date: 2026-06-23

## Status

Accepted

## Context

Contactotomy makes several consequential, hard-to-reverse choices (language,
how it reads/writes contacts, how it decides two cards are the same person).
We want a durable, reviewable record of *why* each choice was made, so future
work — including a possible broadening beyond a single local user — can revisit
decisions with full context rather than re-deriving it.

## Decision

We will use Architecture Decision Records, one per significant decision, in the
Michael Nygard format (Status / Context / Decision / Consequences), stored in
`docs/adr/`. ADRs are immutable once Accepted; a changed decision is captured by
a new ADR that supersedes the old one.

The comprehensive design narrative lives in the design spec
(`docs/superpowers/specs/2026-06-23-contactotomy-design.md`); ADRs capture the
individual decisions distilled from it.

## Consequences

- Every significant decision is greppable and has a rationale.
- Slightly more ceremony per decision; we accept that for the auditability.
- The spec and ADRs must be kept consistent; the spec is the narrative, ADRs are
  the decision log.

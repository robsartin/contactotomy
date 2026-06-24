# 5. Name-gated matching; shared phone/email is corroborating only

Date: 2026-06-23

## Status

Accepted

## Context

The app must cluster cards that represent the same person. Shared phone numbers
and shared emails are tempting as primary keys, but they are routinely shared by
distinct people: married couples, families on a landline, an assistant's number,
a shared household email. Auto-merging on a shared phone would wrongly fuse two
real people into one card.

## Decision

Matching is **name-gated**:

- A shared phone or shared email is **corroborating evidence, not deciding**. It
  yields a high-confidence (auto-merge) cluster **only when the name is also
  compatible** — allowing dropped/abbreviated middle names and common nickname
  pairs (Bob↔Robert), case- and punctuation-insensitive.
- **Different given names are a hard anti-merge signal** that overrides shared
  contact info. Two cards with the same phone but clearly different given names
  are treated as distinct people and never auto-merged.
- Email is weighted slightly stronger than phone for identity, but the same
  name-compatibility gate applies.

Confidence tiers:

- **High / auto-merge:** compatible name AND (shared phone OR shared email).
- **Uncertain / manual review:** name-only matches, or ambiguous cases.
- **Never merged:** shared phone/email with clearly different given names.

Thresholds are tunable constants so aggressiveness can be adjusted without code
changes. Phone numbers are normalized to E.164 (default region US/`+1`).

## Consequences

- Couples and families who share a number remain separate people.
- Some true duplicates with no shared contact info and divergent names fall to
  manual review rather than auto-merge — the safe failure direction.
- The shared-phone-different-name case is a required, locked-in test fixture.

# 4. Mirror the cleaned set into both accounts

Date: 2026-06-23

## Status

Accepted

## Context

The user keeps contacts in both Apple and Google and wants them merged. After
cleaning, the two accounts could be: consolidated to one source of truth; kept as
separate domains; or each populated with the identical cleaned set. The user
chose to keep **both** accounts fully populated and identical.

Because re-importing a cleaned set into an account that still holds the old cards
would recreate duplicates, the import cannot be additive.

## Decision

Produce **one canonical cleaned vCard** (vCard 3.0, UTF-8) and mirror it into both
accounts via a **back up → wipe → import** workflow:

1. Export `apple.vcf` and `google.vcf`.
2. Save backups of both (the app also never mutates inputs).
3. Clean in Contactotomy → export `contacts-clean.vcf`.
4. After confirming backups, delete all existing contacts in **each** account.
5. Import `contacts-clean.vcf` into **both** Apple and Google.

Re-runs repeat the cycle.

## Consequences

- Both accounts end up identical and clean; matches the user's success metric.
- The wipe step is destructive and gated behind explicit "back up first"
  warnings in the docs and UI.
- Wipe + re-import **resets `createdAt`** and **drops Apple group membership** in
  each account (Google labels survive via `CATEGORIES`). Documented plainly.
- Output defaults to a single combined file; per-source split export is a possible
  future option.

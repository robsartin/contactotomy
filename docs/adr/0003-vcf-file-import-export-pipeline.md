# 3. vCard file import/export pipeline (no live account API)

Date: 2026-06-23

## Status

Accepted

## Context

Contacts live in Apple Contacts (synced across the user's iPhone and Mac) and in
Google Contacts. The app could read/write them by (a) talking to live account
APIs (CardDAV / Google People API / macOS Contacts framework), or (b) operating
on user-exported `.vcf` files.

Live APIs would enable richer data (e.g., Apple group membership) but require
auth, network access, and direct mutation of the user's real accounts — more
invasive, more privacy surface, and more failure modes for a personal tool.

## Decision

Operate strictly on **user-exported and re-imported vCard files**. The app:

- Never connects to any account or the network.
- Never modifies its input files; it only writes a new cleaned `.vcf`.
- Provides documented step-by-step export/import instructions for Apple and
  Google.

## Consequences

- Maximum privacy: contact data never leaves the machine and real accounts are
  only touched by the user, through the documented manual steps.
- The user performs export, backup, wipe, and import manually (see ADR-0004).
- **Apple group membership is lost**, because Apple's vCard export omits it — not
  recoverable from the file. Documented as a known limitation; a future optional
  live-Contacts reader could address it. Google labels survive via `CATEGORIES`.
- `createdAt` is best-effort (from `REV`/export metadata), not authoritative.

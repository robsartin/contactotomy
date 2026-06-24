# Contactotomy

Surgical cleanup for contact lists that have accumulated junk across Apple and
Google accounts, imports, and old email addresses.

Contactotomy is a **local, offline macOS desktop app** (Kotlin + Compose Desktop)
that:

- Imports vCard (`.vcf`) exports from Apple Contacts and Google Contacts.
- Finds duplicate cards for the same person using **name-gated** fuzzy matching
  (so two friends who share a phone number stay two people).
- Proposes merges with a **visual side-by-side preview** and per-field toggles.
- Flags cards for deletion using reusable, combinable (AND/OR) rules with
  shell-style wildcards and phone patterns.
- Exports a single cleaned vCard you mirror back into **both** accounts.

Your contacts never leave your machine.

## Status

Design phase. See:

- Design spec: [`docs/superpowers/specs/2026-06-23-contactotomy-design.md`](docs/superpowers/specs/2026-06-23-contactotomy-design.md)
- Architecture Decision Records: [`docs/adr/`](docs/adr/)

## Documentation

The full export → clean → import workflow (including the back-up-and-wipe step
required by the mirror-into-both approach) lives in the design spec and will be
surfaced in-app.

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

Design phase.

## Running and building

**Run from source (development):**

```bash
./gradlew run
```

**Build a distributable macOS DMG:**

```bash
./gradlew packageDmg
```

The DMG is written to `build/compose/binaries/main/dmg/`. Open it and drag Contactotomy to Applications as usual.

> **Note:** The DMG built above is for local use only. Distributing it to other machines requires Apple Developer code signing and notarization, which are not configured here.

## Documentation

- **User guide** (end-to-end: export → clean → import → ongoing sync):
  [`docs/user-guide.md`](docs/user-guide.md)
- **Developer docs** (architecture, module map, data-flow diagrams):
  [`docs/dev/architecture.md`](docs/dev/architecture.md)
- Export / clean / import guide (also shown in-app on the Export screen):
  [`src/main/resources/export-instructions.md`](src/main/resources/export-instructions.md)
- Design spec: [`docs/superpowers/specs/2026-06-23-contactotomy-design.md`](docs/superpowers/specs/2026-06-23-contactotomy-design.md)
- Architecture Decision Records: [`docs/adr/`](docs/adr/)

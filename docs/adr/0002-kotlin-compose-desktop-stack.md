# 2. Kotlin + Compose Desktop stack

Date: 2026-06-23

## Status

Accepted

## Context

Contactotomy is a macOS desktop app whose centerpiece is a rich visual
merge-preview UI, and whose hardest logic is parsing messy real-world vCards
from Apple and Google and normalizing phone numbers for fuzzy matching. The user
was open to Swift or a JVM language.

Options considered:

- **Swift + SwiftUI** — most Mac-native; can use Apple's Contacts framework. But
  vCard-parsing and phone-normalization libraries are thinner, pushing more
  error-prone custom code into the most failure-sensitive area.
- **Kotlin + Compose Desktop** — real desktop app with a strong declarative UI
  toolkit; access to the JVM ecosystem, including the best-in-class libraries for
  this problem domain.
- **Kotlin core + local web UI** — most flexible UI, but a browser app, not the
  requested Mac app.

## Decision

Build with **Kotlin + Compose Desktop**, using:

- **ez-vcard** (BSD) for vCard parsing/serialization — handles Apple/Google
  quirks and round-trips fields we do not explicitly model.
- **libphonenumber** (Apache 2.0, Google's own) for phone normalization to E.164
  and fuzzy phone comparison.
- **Gradle** for build; **JUnit** for tests.

All dependencies are offline with no telemetry.

## Consequences

- Best-available libraries for the riskiest work (vCard + phone handling).
- A genuine, hackable Mac app with a capable UI toolkit for the merge preview.
- Not as natively Mac-styled as SwiftUI; acceptable for a personal tool.
- We cannot use Apple's Contacts framework directly — consistent with the
  file-based pipeline in ADR-0003.

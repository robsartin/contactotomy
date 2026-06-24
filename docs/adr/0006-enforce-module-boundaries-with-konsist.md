# 6. Enforce module boundaries with Konsist

Date: 2026-06-23

## Status

Accepted

## Context

The design separates a pure, headless **core engine** (import, model, matcher,
merger, rules, exporter) from the **Compose UI**. This boundary is what keeps the
engine fully unit-testable and the logic reasoned-about independently of the UI.
Boundaries that are only enforced by convention erode over time.

We want automated, test-time enforcement. ArchUnit (JVM bytecode analysis) works
with Kotlin but reasons about compiled classes and is awkward with Kotlin idioms
(top-level/extension functions, package-level declarations). Konsist is
Kotlin-native, analyzes Kotlin source via the compiler PSI, and runs as ordinary
JUnit tests.

## Decision

Use **Konsist** to enforce architecture rules as JUnit tests run in CI,
including at minimum:

- `core.*` packages must not depend on `ui.*` or on Compose/`androidx.compose.*`.
- The engine must not reference UI types.
- Package and naming conventions agreed for the codebase.

## Consequences

- Module boundaries are verified mechanically on every test run, not just in
  review.
- Kotlin-native rules read naturally and cover Kotlin-specific constructs.
- A small additional test dependency and a set of architecture tests to maintain.
- ArchUnit remains a fallback option if a rule proves hard to express in Konsist.

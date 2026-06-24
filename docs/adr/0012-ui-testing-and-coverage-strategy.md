# 12. UI testing and coverage strategy

Date: 2026-06-24

## Status

Accepted

## Context

Plan 4 introduces a Compose Desktop UI. The repo enforces code-coverage floors
(ADR-0009: line ≥80%, branch ≥60%). Compose rendering code (composable functions,
the application entry point, the native file dialog) is expensive and brittle to
unit-test and is best verified by running the app. Counting it toward line/branch
coverage would either force low-value UI tests or pressure the floors downward,
undermining the gate's meaning for real logic.

## Decision

- **Logic lives in framework-free state holders** (e.g. `AppStore`) and pure
  helpers, separate from composables. This logic is unit-tested and fully counts
  toward coverage.
- **The Compose rendering layer is excluded from coverage** — composable functions,
  `MainKt`/`App` entry points, and thin platform adapters (e.g. the AWT
  `FilePicker` implementation). Excluded via Kover filters (by package/file or an
  exclusion annotation), documented in `build.gradle.kts`.
- **The UI is verified by running the app** (`./gradlew run`) as part of each UI
  sub-plan's "done": launch, exercise the screen, and record a screenshot/notes in
  the PR. This is the acceptance check that replaces unit coverage for rendering.
- Platform-bound, untestable seams (native file dialogs, etc.) are isolated behind
  small interfaces so the surrounding logic stays testable with fakes; only the
  thin real implementation is excluded.

## Consequences

- Coverage floors remain meaningful: they measure logic, not rendering.
- UI correctness is gated by a deliberate run-the-app verification step, not by
  brittle pixel/coverage tests.
- The logic/rendering split is a design constraint for all UI sub-plans (4a–4d):
  keep composables thin, push behavior into testable holders.
- The Kover exclusion list grows as UI packages are added; it must stay scoped to
  genuine rendering/platform code, never used to hide untested logic.

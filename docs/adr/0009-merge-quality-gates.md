# 9. Merge quality gates: CI, agent review, human-merged

Date: 2026-06-24

## Status

Accepted. Coverage threshold figures raised by ADR-0012 (now line ≥ 90%,
branch ≥ 65%); the rest of this ADR stands.

## Context

The codebase has grown a unit-test suite, an architecture-enforcement layer
(Konsist, ADR-0006), and a separation between a pure core engine and the UI. As
work continues, we want changes to land on `main` only after a consistent,
automated set of quality checks, so that `main` always stays green, tested,
covered, and consistently formatted.

This is a solo repository. Some governance controls (notably GitHub's
"require an approving review before merge") are awkward for a single maintainer:
requiring an approving review with only one human creates a self-approval
deadlock, because GitHub does not let you approve your own pull request. We
therefore need a control scheme that enforces quality without depending on a
second reviewer, while still keeping a human in the loop for the final merge
decision.

## Decision

`main` is a protected branch. Changes land **only via pull request**; direct
pushes to `main` are not permitted.

Continuous integration must pass before a pull request can be merged. CI runs
`./gradlew check`, which includes:

- **Unit tests**, including the Konsist architecture tests (ADR-0006).
- **Code-coverage thresholds**, measured by Kover: a **minimum of 80% line
  coverage and 60% branch coverage**. These are *floors* to be raised over time
  as the suite matures, not targets to sit at. They must never be lowered to make
  a build pass; instead, add tests.
- **Code formatting**, enforced by Spotless with ktlint. CI runs
  `spotlessCheck`; developers run `spotlessApply` locally to auto-fix.

An **automated agent review** runs on every pull request via the **managed
Claude GitHub App** (Code Review), configured from the Claude admin settings for
this repository — not a self-hosted GitHub Actions workflow, so no API-key secret
is managed in the repo. It posts its findings as a review comment. This review is
**advisory**: it informs the human but is not a hard gate and never blocks merge.

A **human approves the merge**. After CI is green, a human merges the pull
request via the GitHub UI. The agent never auto-merges. Because this is a solo
repository, a *required approving review* is intentionally **not** enforced (it
would create a self-approval deadlock); the human-merge-after-green-CI step is
the equivalent control.

Enforcement is implemented with a GitHub **repository ruleset** on `main`
(requires a pull request, requires the `build` status check, allows only
squash merges, blocks force-pushes and deletion). Because GitHub branch
protection/rulesets require a paid plan on private repositories, the repository
is **public**. The repository **admin role retains ruleset bypass** as a safety
valve: this is required to bootstrap CI (a brand-new workflow does not run on a
pull request until it exists on the default branch, so the very first
gates-introducing PR is merged via admin bypass), and to recover if CI is ever
wedged. Routine changes still go through PR + green CI + human merge.

## Consequences

- `main` stays green, tested, covered, and consistently formatted, because every
  change is gated by `./gradlew check` in CI before merge.
- Coverage cannot silently regress below the floors, and the floors create
  pressure to add tests rather than weaken thresholds.
- Formatting is mechanical and uncontested: ktlint settles style, and
  `spotlessApply` makes fixing trivial.
- Every pull request gets an automated review pass, surfacing issues early
  without blocking the maintainer on a second human reviewer.
- The human retains the final merge decision; nothing merges itself.
- The trade-off of the solo-repo model is that there is no enforced second-pair-
  of-eyes approval. The advisory agent review plus the deliberate human merge
  step mitigate this; if the project gains additional maintainers, a required
  approving review should be revisited in a superseding ADR.

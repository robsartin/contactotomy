# 10. Issue-driven pull requests

Date: 2026-06-24

## Status

Accepted

## Context

Pull requests so far have carried their full rationale only in the PR
description. As the project accumulates history, we want a durable, linkable
record of *why* each change was undertaken that is independent of the PR, easy to
find from the issue tracker, and that ties planned work to the change that
delivered it. GitHub issues provide this: a stable home for the problem
statement, discussion, and acceptance criteria, separate from the implementation.

This complements ADR-0009 (merge quality gates): ADR-0009 governs *how* a change
is verified and merged; this ADR governs *how a change is tracked* from intent to
delivery.

## Decision

Every unit of work starts with a **GitHub issue** that has a real description —
the problem or goal, relevant context, and acceptance criteria. The branch and
its **pull request reference that issue**, using a closing keyword
(`Closes #<n>`) in the PR description so the issue is automatically closed when
the PR merges.

Concretely:

- Create the issue first (or at the latest when opening the PR), with a
  meaningful title and description — not a placeholder.
- Name the branch and PR for the work, and put `Closes #<n>` (or `Fixes #<n>`) in
  the PR body.
- Trivial, no-discussion chores (e.g. a typo fix) may skip the issue, but
  anything that warrants review or carries rationale gets one.

## Consequences

- Intent and rationale live in a durable, searchable place, linked bidirectionally
  to the delivering PR.
- The issue tracker reflects what is planned, in progress, and done.
- Slightly more ceremony per change; accepted for the traceability, and waived for
  trivial chores.
- This ADR is itself delivered under the new convention (its PR links the issue
  that introduced the policy).

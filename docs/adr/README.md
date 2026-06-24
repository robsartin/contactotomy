# Architecture Decision Records

We record significant, hard-to-reverse decisions as ADRs (Michael Nygard format).
Each ADR is immutable once **Accepted**; to change a decision, add a new ADR that
**supersedes** the old one (and update the old one's status to *Superseded by
ADR-NNNN*).

## Format

Each record has: **Status**, **Context**, **Decision**, **Consequences**.
Status is one of: Proposed, Accepted, Superseded, Deprecated.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-kotlin-compose-desktop-stack.md) | Kotlin + Compose Desktop stack | Accepted |
| [0003](0003-vcf-file-import-export-pipeline.md) | vCard file import/export pipeline (no live account API) | Accepted |
| [0004](0004-mirror-clean-set-into-both-accounts.md) | Mirror the cleaned set into both accounts | Accepted |
| [0005](0005-name-gated-matching.md) | Name-gated matching; shared phone/email is corroborating only | Accepted |
| [0006](0006-enforce-module-boundaries-with-konsist.md) | Enforce module boundaries with Konsist | Accepted |
| [0007](0007-deletion-rule-engine.md) | Deletion-rule engine: shell-glob, AND/OR, review-gated | Accepted |
| [0008](0008-clustering-strategy.md) | Clustering: transitive on strong links only; surname changes gated | Accepted |
| [0009](0009-merge-quality-gates.md) | Merge quality gates: CI (tests/coverage/format/Konsist), agent review, human-merged | Accepted |

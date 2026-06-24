# 7. Deletion-rule engine: shell-glob, AND/OR, review-gated

Date: 2026-06-23

## Status

Accepted

## Context

Beyond merging duplicates, the user wants to flag cards for deletion by patterns
over email, name, phone, address, and structural emptiness — for example
`*@indeed.com`, `sartin@*`, `512-???-????`, and "any card with no name and no
phone." These need to be reusable and combinable, and must never delete anything
without review.

Pattern syntax could be full regex or shell-style globbing. Shell-style covers
all the user's examples and is far friendlier to write and read.

## Decision

A **deletion-rule engine** with:

- **Shell-style wildcards** (`*` = any run, `?` = one character),
  **case-insensitive**, on text fields (email, name, org, address, URL, notes).
- **Phone patterns** with `?` digit slots over normalized numbers
  (`512-???-????`).
- **Structural predicates** (`no name AND no phone`, `no email`, `empty card`,
  `created before <date>`, `source = …`). A `never contacted` predicate is
  stubbed (always skipped) pending future usage signals.
- **AND/OR composition** with nestable groups.
- Rules are **named, saved** to a human-editable local file, and **reusable**
  across runs; ships with starter rules from the user's examples.
- Rules run on the **post-merge** set by default and always produce a
  **review list** (each hit annotated with the rule/condition that matched).
  Nothing is deleted without explicit batch confirmation.

## Consequences

- Friendly, sufficient matching; an advanced regex toggle is a possible future
  addition.
- Saved rules make repeat cleanups fast.
- Review-gated deletion keeps the destructive path safe, consistent with the
  app's overall caution.

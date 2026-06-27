# Starter Rule Curation + Remove NEVER_CONTACTED — Design Spec (#39)

Date: 2026-06-27
Status: Approved (design); pending implementation plan
Builds on: #39's PR #40 (NO_PHONE predicate + `rules/contact-cleanup.json` library).
Tracking issue: #39

## 1. Purpose

Two deferred items from the deletion-rules work:
1. Promote a curated, low-false-positive subset of the contact-cleanup library
   into the **pre-loaded starter rules**, so a fresh run flags the obvious junk
   without the user importing the JSON.
2. Remove the dead `NEVER_CONTACTED` predicate stub (always false; no data
   source). Recency stays a future feature.

## 2. Decisions

- **Starter set = existing 4 examples + 6 safe defaults** (the "safe defaults"
  choice). New starters, as Kotlin literals in `RuleSet.starter()`:
  - `empty cards` — `Predicate(EMPTY_CARD)`
  - `name is an email address` — `TextMatch(TextField.NAME, "*@*")`
  - `no-reply senders` — `Or` of `TextMatch(EMAIL, "no-reply@*")`, `"noreply@*"`,
    `"donotreply@*"`, `"do-not-reply@*"`
  - `premium rate (1-900)` — `PhoneMatch("900-???-????")`
  - `placeholder names` — `Or` of `TextMatch(NAME, "*test*")`, `"*unknown*"`,
    `"*no name*"`, `"*new contact*"`, `"*duplicate*"`, `"*do not use*"`
  - `automated sender with no identity` — `And(Or(<the 4 no-reply globs>),
    Predicate(NO_NAME_AND_NO_PHONE))`
  Existing starters kept: `old job (indeed)`, `my own addresses` (`sartin@*`),
  `austin area code` (`512-???-????`), `no name and no phone`. **Total: 10.**
  (`no name and no phone` is already a starter — not duplicated.)
- **Aggressive rules stay load-on-demand** in `rules/contact-cleanup.json` (role
  mailboxes, toll-free, recruiter sites, unreachable, notes-flagged). The JSON
  remains the full superset.
- **Remove `NEVER_CONTACTED`** from `PredicateKind`, `Predicates.evaluate`, and
  `ConditionEvaluator.describePredicate`. Nothing shipped references it (not in
  starters or the library JSON).
- **Naming exactness:** starter rule names match the library JSON names verbatim
  (so the two stay recognizably the same rule).

## 3. Implementation surface

- `core/rules/StarterRules.kt` — add the 6 rules (conditions as above), keep the 4.
- `core/rules/RuleTypes.kt` — drop `NEVER_CONTACTED` from `PredicateKind`.
- `core/rules/Predicates.kt` — drop its `when` branch.
- `core/rules/ConditionEvaluator.kt` — drop its `describePredicate` branch.
- Tests: `StarterRulesTest` (the 10 expected names; the 6 new conditions present);
  update `PredicatesTest` (remove the `NEVER_CONTACTED` stub assertion, keep the
  `SOURCE_IS` assertions).

## 4. Testing

- `StarterRulesTest`: `RuleSet.starter().rules` has 10 rules; names contain the 6
  new ones + the 4 existing; spot-check a couple conditions (e.g. `empty cards` is
  `Predicate(EMPTY_CARD)`, `name is an email address` is `TextMatch(NAME, "*@*")`).
- `PredicatesTest`: the `source is and never contacted stub` test loses its
  `NEVER_CONTACTED` line (rename to `source is`); all other predicate tests
  unchanged. Confirm no other test references `NEVER_CONTACTED` (grep).
- `./gradlew check` green (line ≥90 / branch ≥70 — removing a `when` arm removes a
  branch; adding tested starter rules adds covered code); Spotless; Konsist.

## 5. Scope

In scope: `StarterRules.kt`, the `NEVER_CONTACTED` removal across
`RuleTypes`/`Predicates`/`ConditionEvaluator`, and the two test files. Out of
scope: the recency feature itself (future, needs a usage-data import); #42
(phones/emails selection), #43 (clear notes/title).

## 6. Notes

- The six starter conditions duplicate definitions that also live in
  `rules/contact-cleanup.json` (independent sources of truth). Accepted: it
  matches the existing `starter()` pattern (which already hardcodes its 4), and
  both are covered by tests. If drift becomes a problem later, starters could be
  derived from the JSON resource — out of scope here.
- Removing a `PredicateKind` value is a serialization-breaking change for any
  hand-written rule JSON containing `"NEVER_CONTACTED"`. None ships; acceptable.

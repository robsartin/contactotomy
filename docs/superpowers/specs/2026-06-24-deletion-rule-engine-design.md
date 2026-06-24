# Deletion-Rule Engine — Design Spec

Date: 2026-06-24
Status: Approved (design); pending implementation plan
Builds on: `2026-06-23-contactotomy-design.md` (§8) and ADR-0007.
Tracking issue: #6

## 1. Purpose

A pure-Kotlin engine that flags contacts for **review-gated** deletion using
reusable, combinable rules. It evaluates a `RuleSet` against a list of contacts
and returns the flagged contacts, each annotated with which rule/condition
matched. Nothing is deleted without explicit approval. Rules persist to a
human-editable JSON file.

No UI, no persistence beyond the rules file, no pipeline wiring. Everything lives
under `com.robsartin.contactotomy.core.rules` and must not import UI/Compose
(Konsist-enforced, ADR-0006).

## 2. Key decisions (this plan)

- **Rules serialize to JSON** via `kotlinx.serialization` (adds the
  `kotlinx-serialization-json` dependency and the Kotlin serialization plugin).
- **Boolean operators are AND / OR / NOT**, nestable (a mild extension of
  ADR-0007's AND/OR).
- **Phone patterns match the national significant number** (so `512-???-????`
  flags any 512 area-code number), via libphonenumber.
- **`CREATED_BEFORE` with a missing `createdAt` does not flag** (cautious "keep"
  default, consistent with best-effort dates).
- **Scope is the engine + serialization + apply**, not the review UI or pipeline
  placement.

## 3. Module & core types

Package `com.robsartin.contactotomy.core.rules`.

```kotlin
enum class TextField { EMAIL, NAME, ORG, ADDRESS, URL, NOTES }
enum class PredicateKind {
    NO_NAME_AND_NO_PHONE, NO_EMAIL, EMPTY_CARD, CREATED_BEFORE, SOURCE_IS, NEVER_CONTACTED
}

sealed interface Condition
data class TextMatch(val field: TextField, val glob: String) : Condition
data class PhoneMatch(val pattern: String) : Condition
data class Predicate(
    val kind: PredicateKind,
    val before: java.time.Instant? = null,   // for CREATED_BEFORE
    val source: Source? = null,              // for SOURCE_IS
) : Condition
data class And(val of: List<Condition>) : Condition
data class Or(val of: List<Condition>) : Condition
data class Not(val of: Condition) : Condition

data class Rule(val name: String, val condition: Condition)
data class RuleSet(val rules: List<Rule>)

data class RuleMatch(val ruleName: String, val reason: String)
data class Flagged(val contact: Contact, val matches: List<RuleMatch>)
```

The engine takes `List<Contact>` in (the pipeline feeds it the post-merge set per
ADR-0007); it does not depend on the matcher/merger.

## 4. Matching semantics

### 4.1 TextMatch (shell glob, case-insensitive)
`*` = any run (including empty), `?` = exactly one char; all other regex
metacharacters are escaped. Compiled to a `Regex` once. Matches if **any** value
of the field matches (e.g. any email matches `*@indeed.com`). The `NAME` field is
tested against the formatted name and the given/family parts. An empty field does
not match.

### 4.2 PhoneMatch
The pattern is reduced to digits and `?` (separators ignored: `512-???-????` →
`512???????`). Each contact phone (stored E.164, e.g. `+15125551234`) is reduced
to its **national significant number** via libphonenumber (`5125551234`); `?`
matches one digit, literal digits must match exactly, lengths must be equal.
Matches if any phone matches. If a phone cannot be parsed, fall back to matching
the pattern as a **suffix** of the E.164 digits.

### 4.3 Predicates
- `NO_NAME_AND_NO_PHONE`: no given/family/formatted name AND no phones.
- `NO_EMAIL`: emails empty.
- `EMPTY_CARD`: no name, phones, emails, org, addresses, urls, or notes.
- `CREATED_BEFORE` (uses `before`): `createdAt != null && createdAt < before`;
  null `createdAt` → **false** (not flagged).
- `SOURCE_IS` (uses `source`): contact's source equals `source`.
- `NEVER_CONTACTED`: **stub**, always false (future usage-signals hook).

### 4.4 Combinators
`And` = all match; `Or` = any match; `Not` = negation. Nestable to any depth. An
empty `And` is vacuously true; an empty `Or` is false (documented).

### 4.5 Reasons
When a rule matches a contact, the engine records `RuleMatch(ruleName, reason)`
where `reason` renders the leaf condition(s) responsible (e.g.
`email matches *@indeed.com`). For `Or`, only satisfied branches are named; for
`And`, the satisfied conjunction; `Not` is rendered as `not (...)`. This text is
what the review screen shows per flagged card.

## 5. Serialization

`kotlinx.serialization` JSON. The `Condition` hierarchy is polymorphic with stable
discriminators: `text`, `phone`, `predicate`, `and`, `or`, `not`. `RuleSet`
(de)serializes to a pretty-printed JSON file. `Instant` serializes as ISO-8601;
enums by name.

```json
{
  "rules": [
    { "name": "old job", "condition": { "type": "text", "field": "EMAIL", "glob": "*@indeed.com" } },
    { "name": "junk", "condition": { "type": "predicate", "kind": "NO_NAME_AND_NO_PHONE" } }
  ]
}
```

`RuleStore.load(path): RuleSet` and `RuleStore.save(path, ruleSet)`. Malformed
JSON or unknown discriminators/enum values throw a clear error (rules are never
silently dropped).

## 6. Starter rules

`RuleSet.starter()` returns the seed set (also writable to a fresh rules file):
- `TextMatch(EMAIL, "*@indeed.com")`
- `TextMatch(EMAIL, "sartin@*")`
- `PhoneMatch("512-???-????")`
- `Predicate(NO_NAME_AND_NO_PHONE)`

Defaults to keep, edit, or delete — not hardcoded behavior.

## 7. Engine & applying deletions

```kotlin
object RuleEngine { fun evaluate(contacts: List<Contact>, ruleSet: RuleSet): List<Flagged> }
fun applyDeletions(contacts: List<Contact>, approvedIds: Set<String>): List<Contact>
```

- `evaluate`: for each contact, evaluate every rule; collect matching rules into a
  `Flagged`. Contacts hit by no rule are omitted. A contact matched by multiple
  rules appears once with multiple `RuleMatch` entries. Pure, deterministic, input
  order preserved.
- `applyDeletions`: returns the list with approved contacts removed, order
  preserved. The review UI (Plan 4) supplies the approved ids; nothing deletes
  without that approval (ADR-0007).

## 8. Testing (TDD, pure/headless)

- Glob: `*@indeed.com`, `sartin@*`, single-char `?`, case-insensitivity, literal
  dot escaping.
- Phone: `512-???-????` flags `+15125551234`, rejects a different area code;
  unparseable-phone suffix fallback.
- Predicates: each kind, including `CREATED_BEFORE` null-`createdAt` → not flagged,
  and `NEVER_CONTACTED` → false.
- Combinators: AND all-match, OR any-match, NOT negation, nested; empty-And true,
  empty-Or false.
- Reasons: matched leaf named correctly; OR names only the satisfied branch.
- Serialization: starter ruleset → JSON → parse → equal; malformed JSON throws.
- `applyDeletions`: removes approved, keeps others, preserves order, determinism.

Konsist boundary test continues to enforce no UI/Compose imports in `core`.

## 9. Scope boundary

In scope: the `rules` package — AST, matchers, predicates, reasons, engine,
serialization, starter rules, `applyDeletions`. Out of scope: the review UI and
wiring deletion into the post-merge pipeline (Plan 4); real usage signals.

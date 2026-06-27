# Starter Rule Curation + Remove NEVER_CONTACTED Implementation Plan (#39)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pre-load six safe-default cleanup rules into the starter set (total 10), and remove the dead always-false `NEVER_CONTACTED` predicate.

**Architecture:** Two independent core/rules edits: extend `RuleSet.starter()` with Kotlin-literal rules matching the library JSON names; delete `NEVER_CONTACTED` from the `PredicateKind` enum and its two `when` arms. Tests cover both.

**Tech Stack:** Kotlin, kotlinx-serialization, kotlin-test, Kover (line ≥90 / branch ≥70), Spotless/ktlint, Konsist.

## Global Constraints
- Only `core/rules/` changes; Konsist keeps `core` UI-free. macOS: plain `./gradlew test` (no xvfb).
- `./gradlew check` stays green (line ≥90 / branch ≥70, Spotless, Konsist).
- Starter rule names match the library JSON (`rules/contact-cleanup.json`) verbatim.

Branch: `39-starter-rules` (off `main`). Issue: #39. Spec: `docs/superpowers/specs/2026-06-27-starter-rules-design.md`.

## File Structure
- Modify `core/rules/StarterRules.kt` — add 6 rules.
- Modify `core/rules/RuleTypes.kt`, `core/rules/Predicates.kt`, `core/rules/ConditionEvaluator.kt` — remove `NEVER_CONTACTED`.
- Tests: new `StarterRulesTest.kt`; modify `PredicatesTest.kt`.

---

### Task 1: Expand the starter rule set

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/rules/StarterRules.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/StarterRulesTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `StarterRulesTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterRulesTest {
    private val starter = RuleSet.starter()

    @Test fun `starter set has the ten curated rules`() {
        assertEquals(10, starter.rules.size)
        val names = starter.rules.map { it.name }.toSet()
        assertTrue(
            names.containsAll(
                setOf(
                    "old job (indeed)", "my own addresses", "austin area code", "no name and no phone",
                    "empty cards", "name is an email address", "no-reply senders",
                    "premium rate (1-900)", "placeholder names", "automated sender with no identity",
                ),
            ),
            "missing starters: $names",
        )
    }

    @Test fun `new safe-default conditions are correct`() {
        val by = starter.rules.associateBy { it.name }
        assertEquals(Predicate(PredicateKind.EMPTY_CARD), by["empty cards"]?.condition)
        assertEquals(TextMatch(TextField.NAME, "*@*"), by["name is an email address"]?.condition)
        assertEquals(PhoneMatch("900-???-????"), by["premium rate (1-900)"]?.condition)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.StarterRulesTest"`
Expected: FAIL — starter set currently has 4 rules, not 10.

- [ ] **Step 3: Implement**

Replace the body of `RuleSet.Companion.starter()` in `StarterRules.kt` with (keep the existing 4, add 6):

```kotlin
fun RuleSet.Companion.starter(): RuleSet =
    RuleSet(
        listOf(
            Rule("old job (indeed)", TextMatch(TextField.EMAIL, "*@indeed.com")),
            Rule("my own addresses", TextMatch(TextField.EMAIL, "sartin@*")),
            Rule("austin area code", PhoneMatch("512-???-????")),
            Rule("no name and no phone", Predicate(PredicateKind.NO_NAME_AND_NO_PHONE)),
            Rule("empty cards", Predicate(PredicateKind.EMPTY_CARD)),
            Rule("name is an email address", TextMatch(TextField.NAME, "*@*")),
            Rule(
                "no-reply senders",
                Or(
                    listOf(
                        TextMatch(TextField.EMAIL, "no-reply@*"),
                        TextMatch(TextField.EMAIL, "noreply@*"),
                        TextMatch(TextField.EMAIL, "donotreply@*"),
                        TextMatch(TextField.EMAIL, "do-not-reply@*"),
                    ),
                ),
            ),
            Rule("premium rate (1-900)", PhoneMatch("900-???-????")),
            Rule(
                "placeholder names",
                Or(
                    listOf(
                        TextMatch(TextField.NAME, "*test*"),
                        TextMatch(TextField.NAME, "*unknown*"),
                        TextMatch(TextField.NAME, "*no name*"),
                        TextMatch(TextField.NAME, "*new contact*"),
                        TextMatch(TextField.NAME, "*duplicate*"),
                        TextMatch(TextField.NAME, "*do not use*"),
                    ),
                ),
            ),
            Rule(
                "automated sender with no identity",
                And(
                    listOf(
                        Or(
                            listOf(
                                TextMatch(TextField.EMAIL, "no-reply@*"),
                                TextMatch(TextField.EMAIL, "noreply@*"),
                                TextMatch(TextField.EMAIL, "donotreply@*"),
                                TextMatch(TextField.EMAIL, "do-not-reply@*"),
                            ),
                        ),
                        Predicate(PredicateKind.NO_NAME_AND_NO_PHONE),
                    ),
                ),
            ),
        ),
    )
```

(Keep the existing file's doc comment and `package`/imports; `Or`/`And`/`TextMatch`/`PhoneMatch`/`Predicate`/`TextField`/`PredicateKind` are all in the same package.)

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.StarterRulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/StarterRules.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/rules/StarterRulesTest.kt
git commit -m "feat(core): pre-load 6 safe-default cleanup rules as starters (#39)"
```

---

### Task 2: Remove the dead `NEVER_CONTACTED` predicate

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/rules/Predicates.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluator.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/PredicatesTest.kt`

- [ ] **Step 1: Update the failing test first (TDD: red via compile failure)**

In `PredicatesTest.kt`, the test `source is and never contacted stub` currently ends with a `NEVER_CONTACTED` assertion. Rename it to `source is` and delete the `NEVER_CONTACTED` line so it reads:

```kotlin
    @Test fun `source is`() {
        assertTrue(
            Predicates.evaluate(
                p(PredicateKind.SOURCE_IS, source = Source.GOOGLE),
                contact("1", source = Source.GOOGLE),
            ),
        )
        assertFalse(
            Predicates.evaluate(
                p(PredicateKind.SOURCE_IS, source = Source.APPLE),
                contact("1", source = Source.GOOGLE),
            ),
        )
    }
```

- [ ] **Step 2: Run to verify it fails to compile**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.PredicatesTest"`
Expected: still COMPILES/PASSES at this point (the enum value still exists). This step's real purpose is to remove the only test dependency on `NEVER_CONTACTED` before deleting it. Proceed.

- [ ] **Step 3: Remove the enum value and its two `when` arms**

In `RuleTypes.kt`, delete `NEVER_CONTACTED,` from the `PredicateKind` enum (leaving `NO_NAME_AND_NO_PHONE, NO_PHONE, NO_EMAIL, EMPTY_CARD, CREATED_BEFORE, SOURCE_IS`).

In `Predicates.kt`, delete the line:
```kotlin
            PredicateKind.NEVER_CONTACTED -> false
```

In `ConditionEvaluator.kt` `describePredicate`, delete the line:
```kotlin
            PredicateKind.NEVER_CONTACTED -> "never contacted"
```

(Both `when (...)` expressions over `PredicateKind` remain exhaustive after removal — no `else` needed.)

- [ ] **Step 4: Confirm no other references**

Run: `grep -rn "NEVER_CONTACTED" src/` — expect **no matches**. (If any remain — e.g. in another test — remove them; none are expected.)

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.*"`
Expected: PASS (compiles cleanly; the two `when` blocks are exhaustive without `NEVER_CONTACTED`).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt \
        src/main/kotlin/com/robsartin/contactotomy/core/rules/Predicates.kt \
        src/main/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluator.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/rules/PredicatesTest.kt
git commit -m "refactor(core): remove dead NEVER_CONTACTED predicate stub (#39)"
```

---

### Task 3: Full gate, push, PR

- [ ] **Step 1: Gate** — `./gradlew check` → BUILD SUCCESSFUL (tests, Kover 90/70, Spotless, Konsist). `spotlessApply` + amend if Spotless flags.
- [ ] **Step 2: Push** — `git push -u origin 39-starter-rules`
- [ ] **Step 3: PR**

```bash
gh pr create --base main --head 39-starter-rules \
  --title "Curate starter rules; remove dead NEVER_CONTACTED predicate (#39)" \
  --body "Implements the deferred parts of #39 per docs/superpowers/specs/2026-06-27-starter-rules-design.md. Pre-loads 6 safe-default cleanup rules into RuleSet.starter() (total 10): empty cards, name-is-an-email, no-reply senders, premium 1-900, placeholder names, automated-sender-no-identity (alongside the existing examples). Aggressive rules stay load-on-demand in rules/contact-cleanup.json. Removes the dead always-false NEVER_CONTACTED predicate (recency stays a future feature needing a usage-data source). Core/rules only; no UI change."
```

- [ ] **Step 4: Watch CI** — `gh pr checks <PR#> --watch`; on green hand off for merge.

---

## Self-Review

**Spec coverage:** §2 starter set (4 existing + 6 new, names verbatim) → Task 1; §2 remove NEVER_CONTACTED across RuleTypes/Predicates/ConditionEvaluator + PredicatesTest → Task 2; §4 tests → Tasks 1–2; gate → Task 3. ✓
**Placeholder scan:** none — full code/edits per step; the grep step is a concrete verification, not a vague directive. ✓
**Type consistency:** `RuleSet.starter()` returns `RuleSet`; rule names match the library JSON and the `StarterRulesTest` expectations exactly; `Predicate(PredicateKind.EMPTY_CARD)`, `TextMatch(TextField.NAME, "*@*")`, `PhoneMatch("900-???-????")`, `Or(listOf(...))`, `And(listOf(...))` match the existing `RuleTypes` constructors; `PredicateKind` after removal still covers every `when` arm in `Predicates`/`ConditionEvaluator`. ✓
**Note:** Removing a `PredicateKind` value is serialization-breaking for hand-written JSON using `"NEVER_CONTACTED"` — none ships (spec §6), accepted.

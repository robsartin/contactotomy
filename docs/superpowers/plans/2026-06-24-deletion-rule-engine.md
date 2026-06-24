# Deletion-Rule Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure-Kotlin deletion-rule engine — a JSON-serializable condition AST (glob/phone/predicate + AND/OR/NOT), an evaluator that flags contacts with per-rule reasons, starter rules, and a pure `applyDeletions`.

**Architecture:** New package `com.robsartin.contactotomy.core.rules` (pure logic, no UI/Compose — Konsist-enforced). Rule data types stay plain until serialization is added in the final tasks. Builds on Plan 1's `Contact`/`Source` model. See `docs/superpowers/specs/2026-06-24-deletion-rule-engine-design.md` and ADR-0007/0011. Tracking issue #6.

**Tech Stack:** Kotlin (JVM, toolchain 21), JUnit 5, libphonenumber (already a dependency), `kotlinx-serialization-json` (added in Task 1), `./gradlew` (Gradle 8.13).

---

## Conventions for every task

- Build/test with `./gradlew` (NEVER system `gradle`).
- Strict TDD: failing test first → see it fail → minimal implementation → see it pass.
- The repo enforces quality gates (ADR-0009): before each commit, run
  `./gradlew spotlessApply` (auto-format) then **`./gradlew check`** (tests +
  Kover coverage floors line ≥80% / branch ≥60% + spotlessCheck + Konsist) and
  ensure it is BUILD SUCCESSFUL. Commit only when green.
- No UI/Compose imports anywhere in `core`.
- Branch is `6-deletion-rule-engine` (already checked out); commit there.

Existing model (Plan 1), for reference — do not change except where a task says so:

```kotlin
// com.robsartin.contactotomy.core.model
enum class Source { APPLE, GOOGLE, FILE }
data class ContactName(val prefix: String?=null, val given: String?=null, val middle: String?=null,
                       val family: String?=null, val suffix: String?=null, val formatted: String?=null)
data class Contact(val id: String, val source: Source, val name: ContactName,
    val phones: List<String> = emptyList(), val rawPhones: List<String> = emptyList(),
    val emails: List<String> = emptyList(), val addresses: List<String> = emptyList(),
    val org: String?=null, val title: String?=null, val urls: List<String> = emptyList(),
    val notes: String?=null, val categories: List<String> = emptyList(),
    val createdAt: java.time.Instant?=null, val modifiedAt: java.time.Instant?=null, val rawVCard: String)
```

A reusable test helper (define as a `private` member of each test class that needs it, to avoid top-level overload clashes within a package):

```kotlin
private fun contact(
    id: String, given: String? = null, family: String? = null, formatted: String? = null,
    phones: List<String> = emptyList(), emails: List<String> = emptyList(),
    org: String? = null, addresses: List<String> = emptyList(), urls: List<String> = emptyList(),
    notes: String? = null, source: com.robsartin.contactotomy.core.model.Source = com.robsartin.contactotomy.core.model.Source.APPLE,
    createdAt: java.time.Instant? = null,
) = com.robsartin.contactotomy.core.model.Contact(
    id = id, source = source,
    name = com.robsartin.contactotomy.core.model.ContactName(given = given, family = family, formatted = formatted),
    phones = phones, rawPhones = phones, emails = emails, org = org, addresses = addresses,
    urls = urls, notes = notes, createdAt = createdAt, rawVCard = "",
)
```

## File Structure

- `build.gradle.kts` — add the Kotlin serialization plugin + dependency + Kover exclusion for generated serializers (Task 1).
- `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt` — `TextField`, `PredicateKind`, `Condition` + subtypes, `Rule`, `RuleSet`, `RuleMatch`, `Flagged` (Task 2; serialization annotations added in Task 8).
- `.../rules/Glob.kt` — shell-glob → regex (Task 3).
- `.../rules/PhonePattern.kt` — national-number phone matching (Task 4).
- `.../rules/Predicates.kt` — predicate evaluation (Task 5).
- `.../rules/ConditionEvaluator.kt` — match + reason rendering, combinators (Task 6).
- `.../rules/RuleEngine.kt` — `RuleEngine.evaluate` + `applyDeletions` (Task 7).
- `.../rules/RuleStore.kt` + `.../rules/Serializers.kt` — JSON load/save + Instant serializer (Task 8).
- `.../rules/StarterRules.kt` — `RuleSet.Companion.starter()` (Task 9).
- Tests mirror these under `src/test/kotlin/...`.

---

### Task 1: Add kotlinx.serialization to the build

**Files:**
- Modify: `build.gradle.kts`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/SerializationSanityTest.kt`

- [ ] **Step 1: Add the plugin, dependency, and Kover serializer exclusion**

In `build.gradle.kts`:
- In the `plugins { }` block add: `kotlin("plugin.serialization") version "2.0.21"`
- In `dependencies { }` add: `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")`
- In the existing Kover config, exclude generated serializer classes from coverage (their generated branches would otherwise drag branch coverage below the floor). Add a filter excluding classes matching `*$serializer` (kotlinx generates a synthetic `$serializer` per `@Serializable` type). Consult the Kover version's DSL; the intent is e.g.:
  ```kotlin
  kover {
      reports {
          filters { excludes { classes("*\$serializer") } }
          // ... keep the existing verify { } rules (line >= 80, branch >= 60)
      }
  }
  ```
  Verify the exact `filters`/`excludes` DSL against the installed Kover version and adjust so `./gradlew koverVerify` still runs.

- [ ] **Step 2: Write a sanity test proving the plugin applies**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/SerializationSanityTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationSanityTest {
    @Serializable
    data class Foo(val x: Int)

    @Test
    fun `kotlinx serialization round-trips`() {
        val text = Json.encodeToString(Foo.serializer(), Foo(7))
        assertEquals(Foo(7), Json.decodeFromString(Foo.serializer(), text))
    }
}
```

- [ ] **Step 3: Run the sanity test (it should pass once the plugin is applied)**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.SerializationSanityTest"`
Expected: PASS. If it fails to compile with "serializer not found", the serialization plugin is not applied — fix the `plugins` block.

- [ ] **Step 4: Run the full gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL (tests + koverVerify + spotlessCheck + Konsist).

- [ ] **Step 5: Commit**

```bash
git add build.gradle.kts src/test/kotlin/com/robsartin/contactotomy/core/rules/SerializationSanityTest.kt
git commit -m "build: add kotlinx.serialization plugin and dependency (#6)"
```

---

### Task 2: Rule types (plain, no serialization yet)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleTypesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleTypesTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleTypesTest {
    @Test
    fun `rule holds a name and a condition tree`() {
        val rule = Rule(
            name = "junk",
            condition = And(listOf(
                TextMatch(TextField.EMAIL, "*@indeed.com"),
                Not(Predicate(PredicateKind.NO_EMAIL)),
            )),
        )
        assertEquals("junk", rule.name)
        assertEquals(2, (rule.condition as And).of.size)
    }

    @Test
    fun `flagged groups a contact with its rule matches`() {
        val c = Contact(id = "1", source = Source.APPLE, name = ContactName(given = "A"), rawVCard = "")
        val flagged = Flagged(c, listOf(RuleMatch("junk", "no email")))
        assertEquals("1", flagged.contact.id)
        assertEquals("junk", flagged.matches.single().ruleName)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.RuleTypesTest"`
Expected: FAIL — types unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant

enum class TextField { EMAIL, NAME, ORG, ADDRESS, URL, NOTES }

enum class PredicateKind {
    NO_NAME_AND_NO_PHONE, NO_EMAIL, EMPTY_CARD, CREATED_BEFORE, SOURCE_IS, NEVER_CONTACTED
}

sealed interface Condition

data class TextMatch(val field: TextField, val glob: String) : Condition
data class PhoneMatch(val pattern: String) : Condition
data class Predicate(
    val kind: PredicateKind,
    val before: Instant? = null,
    val source: Source? = null,
) : Condition
data class And(val of: List<Condition>) : Condition
data class Or(val of: List<Condition>) : Condition
data class Not(val of: Condition) : Condition

data class Rule(val name: String, val condition: Condition)
data class RuleSet(val rules: List<Rule>) {
    companion object
}

data class RuleMatch(val ruleName: String, val reason: String)
data class Flagged(val contact: Contact, val matches: List<RuleMatch>)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.RuleTypesTest"`
Expected: PASS (both).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleTypesTest.kt
git commit -m "feat(rules): add condition AST and rule types (#6)"
```

---

### Task 3: Glob matcher

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/Glob.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/GlobTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/GlobTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobTest {
    @Test fun `star matches a run`() {
        assertTrue(Glob.matches("*@indeed.com", "bob@indeed.com"))
        assertTrue(Glob.matches("sartin@*", "sartin@gmail.com"))
    }

    @Test fun `question mark matches exactly one char`() {
        assertTrue(Glob.matches("a?c", "abc"))
        assertFalse(Glob.matches("a?c", "ac"))
        assertFalse(Glob.matches("a?c", "abbc"))
    }

    @Test fun `matching is case-insensitive`() {
        assertTrue(Glob.matches("*@INDEED.com", "x@indeed.com"))
    }

    @Test fun `dot is a literal, not any-char`() {
        assertTrue(Glob.matches("a.b", "a.b"))
        assertFalse(Glob.matches("a.b", "axb"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.GlobTest"`
Expected: FAIL — `Glob` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/Glob.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

/** Shell-style glob matching: `*` = any run, `?` = one char; everything else literal. */
internal object Glob {
    fun matches(glob: String, value: String): Boolean = toRegex(glob).matches(value)

    private fun toRegex(glob: String): Regex {
        val sb = StringBuilder()
        for (ch in glob) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                else -> sb.append(Regex.escape(ch.toString()))
            }
        }
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.GlobTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/Glob.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/GlobTest.kt
git commit -m "feat(rules): add shell-glob matcher (#6)"
```

---

### Task 4: Phone pattern matcher

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/PhonePattern.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/PhonePatternTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/PhonePatternTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhonePatternTest {
    @Test fun `matches by national number area code`() {
        assertTrue(PhonePattern.matches("512-???-????", "+15125551234"))
    }

    @Test fun `rejects a different area code`() {
        assertFalse(PhonePattern.matches("512-???-????", "+13125551234"))
    }

    @Test fun `unparseable phone falls back to suffix match`() {
        // "5551234" cannot be parsed as E.164 (no +country); suffix match on digits.
        assertTrue(PhonePattern.matches("???????", "5551234"))
        assertFalse(PhonePattern.matches("000????", "5551234"))
    }

    @Test fun `empty pattern never matches`() {
        assertFalse(PhonePattern.matches("---", "+15125551234"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.PhonePatternTest"`
Expected: FAIL — `PhonePattern` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/PhonePattern.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.google.i18n.phonenumbers.PhoneNumberUtil

/** Matches a digit pattern (`?` = one digit, separators ignored) against a phone's national number. */
internal object PhonePattern {
    private val util = PhoneNumberUtil.getInstance()

    fun matches(pattern: String, phone: String): Boolean {
        val pat = pattern.filter { it.isDigit() || it == '?' }
        if (pat.isEmpty()) return false

        val national = nationalDigits(phone)
        if (national != null && digitMatch(pat, national)) return true

        // Fallback: suffix match against the phone's bare digits.
        val digits = phone.filter { it.isDigit() }
        return digits.length >= pat.length && digitMatch(pat, digits.takeLast(pat.length))
    }

    private fun nationalDigits(phone: String): String? = try {
        util.parse(phone, null).nationalNumber.toString()
    } catch (e: com.google.i18n.phonenumbers.NumberParseException) {
        null
    }

    private fun digitMatch(pattern: String, digits: String): Boolean {
        if (pattern.length != digits.length) return false
        return pattern.indices.all { i -> pattern[i] == '?' || pattern[i] == digits[i] }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.PhonePatternTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/PhonePattern.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/PhonePatternTest.kt
git commit -m "feat(rules): add national-number phone pattern matcher (#6)"
```

---

### Task 5: Predicate evaluation

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/Predicates.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/PredicatesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/PredicatesTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredicatesTest {
    private fun p(kind: PredicateKind, before: Instant? = null, source: Source? = null) =
        Predicate(kind, before, source)

    @Test fun `no name and no phone`() {
        assertTrue(Predicates.evaluate(p(PredicateKind.NO_NAME_AND_NO_PHONE), contact("1")))
        assertFalse(Predicates.evaluate(p(PredicateKind.NO_NAME_AND_NO_PHONE), contact("1", given = "Al")))
        assertFalse(Predicates.evaluate(p(PredicateKind.NO_NAME_AND_NO_PHONE), contact("1", phones = listOf("+1"))))
    }

    @Test fun `no email and empty card`() {
        assertTrue(Predicates.evaluate(p(PredicateKind.NO_EMAIL), contact("1", given = "Al")))
        assertFalse(Predicates.evaluate(p(PredicateKind.NO_EMAIL), contact("1", emails = listOf("a@b.com"))))
        assertTrue(Predicates.evaluate(p(PredicateKind.EMPTY_CARD), contact("1")))
        assertFalse(Predicates.evaluate(p(PredicateKind.EMPTY_CARD), contact("1", notes = "hi")))
    }

    @Test fun `created before respects null createdAt`() {
        val cutoff = Instant.parse("2020-01-01T00:00:00Z")
        val old = contact("1", given = "Al", createdAt = Instant.parse("2015-01-01T00:00:00Z"))
        val new = contact("2", given = "Al", createdAt = Instant.parse("2024-01-01T00:00:00Z"))
        val unknown = contact("3", given = "Al", createdAt = null)
        assertTrue(Predicates.evaluate(p(PredicateKind.CREATED_BEFORE, before = cutoff), old))
        assertFalse(Predicates.evaluate(p(PredicateKind.CREATED_BEFORE, before = cutoff), new))
        assertFalse(Predicates.evaluate(p(PredicateKind.CREATED_BEFORE, before = cutoff), unknown))
    }

    @Test fun `source is and never contacted stub`() {
        assertTrue(Predicates.evaluate(p(PredicateKind.SOURCE_IS, source = Source.GOOGLE),
            contact("1", source = Source.GOOGLE)))
        assertFalse(Predicates.evaluate(p(PredicateKind.SOURCE_IS, source = Source.APPLE),
            contact("1", source = Source.GOOGLE)))
        assertFalse(Predicates.evaluate(p(PredicateKind.NEVER_CONTACTED), contact("1")))
    }

    private fun contact(
        id: String, given: String? = null, phones: List<String> = emptyList(),
        emails: List<String> = emptyList(), notes: String? = null,
        source: Source = Source.APPLE, createdAt: Instant? = null,
    ) = Contact(
        id = id, source = source, name = ContactName(given = given),
        phones = phones, rawPhones = phones, emails = emails, notes = notes,
        createdAt = createdAt, rawVCard = "",
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.PredicatesTest"`
Expected: FAIL — `Predicates` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/Predicates.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact

internal object Predicates {
    fun evaluate(predicate: Predicate, contact: Contact): Boolean = when (predicate.kind) {
        PredicateKind.NO_NAME_AND_NO_PHONE -> !hasName(contact) && contact.phones.isEmpty()
        PredicateKind.NO_EMAIL -> contact.emails.isEmpty()
        PredicateKind.EMPTY_CARD ->
            !hasName(contact) && contact.phones.isEmpty() && contact.emails.isEmpty() &&
                contact.org.isNullOrBlank() && contact.addresses.isEmpty() &&
                contact.urls.isEmpty() && contact.notes.isNullOrBlank()
        PredicateKind.CREATED_BEFORE -> {
            val before = predicate.before
            val created = contact.createdAt
            before != null && created != null && created.isBefore(before)
        }
        PredicateKind.SOURCE_IS -> predicate.source != null && contact.source == predicate.source
        PredicateKind.NEVER_CONTACTED -> false
    }

    private fun hasName(c: Contact): Boolean =
        !c.name.given.isNullOrBlank() || !c.name.family.isNullOrBlank() || !c.name.formatted.isNullOrBlank()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.PredicatesTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/Predicates.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/PredicatesTest.kt
git commit -m "feat(rules): add predicate evaluation (#6)"
```

---

### Task 6: Condition evaluator (combinators + reasons)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluator.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluatorTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluatorTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionEvaluatorTest {
    private val eval = ConditionEvaluator()

    @Test fun `text match yields a reason`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        assertEquals("email matches *@indeed.com", eval.matchReason(TextMatch(TextField.EMAIL, "*@indeed.com"), c))
    }

    @Test fun `and requires all, reason joins matched leaves`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        val cond = And(listOf(TextMatch(TextField.EMAIL, "*@indeed.com"), Predicate(PredicateKind.NO_NAME_AND_NO_PHONE)))
        assertTrue(eval.matchReason(cond, c)!!.contains("AND"))
        val withName = contact("2", given = "Al", emails = listOf("bob@indeed.com"))
        assertNull(eval.matchReason(cond, withName)) // predicate fails -> AND fails
    }

    @Test fun `or names only the satisfied branch`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        val cond = Or(listOf(TextMatch(TextField.EMAIL, "*@indeed.com"), TextMatch(TextField.EMAIL, "*@oldjob.com")))
        assertEquals("email matches *@indeed.com", eval.matchReason(cond, c))
    }

    @Test fun `not negates`() {
        val c = contact("1", emails = listOf("a@b.com"))
        // NOT(NO_EMAIL) is true because the card HAS an email
        assertTrue(eval.matchReason(Not(Predicate(PredicateKind.NO_EMAIL)), c)!!.startsWith("NOT"))
        // NOT(has email) i.e. NOT(NOT NO_EMAIL)... simpler: NOT over a matching condition -> null
        assertNull(eval.matchReason(Not(TextMatch(TextField.EMAIL, "*@b.com")), c))
    }

    @Test fun `empty and is true, empty or is false`() {
        val c = contact("1")
        assertTrue(eval.matchReason(And(emptyList()), c) != null)
        assertNull(eval.matchReason(Or(emptyList()), c))
    }

    private fun contact(
        id: String, given: String? = null, emails: List<String> = emptyList(),
        phones: List<String> = emptyList(),
    ) = Contact(
        id = id, source = Source.APPLE, name = ContactName(given = given),
        phones = phones, rawPhones = phones, emails = emails, rawVCard = "",
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.ConditionEvaluatorTest"`
Expected: FAIL — `ConditionEvaluator` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluator.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact

/** Evaluates a Condition against a Contact, returning a human reason when it matches. */
internal class ConditionEvaluator {

    /** Reason string if [condition] matches [contact], otherwise null. */
    fun matchReason(condition: Condition, contact: Contact): String? = when (condition) {
        is TextMatch ->
            if (textValues(condition.field, contact).any { Glob.matches(condition.glob, it) }) describe(condition) else null
        is PhoneMatch ->
            if (contact.phones.any { PhonePattern.matches(condition.pattern, it) }) describe(condition) else null
        is Predicate ->
            if (Predicates.evaluate(condition, contact)) describe(condition) else null
        is And -> {
            val parts = condition.of.map { matchReason(it, contact) }
            when {
                condition.of.isEmpty() -> "(always)"
                parts.all { it != null } -> parts.filterNotNull().joinToString(" AND ")
                else -> null
            }
        }
        is Or -> {
            val parts = condition.of.mapNotNull { matchReason(it, contact) }
            if (parts.isNotEmpty()) parts.joinToString(" OR ") else null
        }
        is Not ->
            if (matchReason(condition.of, contact) == null) "NOT (${describe(condition.of)})" else null
    }

    /** Static rendering of a condition, independent of any contact. */
    fun describe(condition: Condition): String = when (condition) {
        is TextMatch -> "${condition.field.name.lowercase()} matches ${condition.glob}"
        is PhoneMatch -> "phone matches ${condition.pattern}"
        is Predicate -> describePredicate(condition)
        is And -> condition.of.joinToString(" AND ", "(", ")") { describe(it) }
        is Or -> condition.of.joinToString(" OR ", "(", ")") { describe(it) }
        is Not -> "NOT (${describe(condition.of)})"
    }

    private fun describePredicate(p: Predicate): String = when (p.kind) {
        PredicateKind.NO_NAME_AND_NO_PHONE -> "no name and no phone"
        PredicateKind.NO_EMAIL -> "no email"
        PredicateKind.EMPTY_CARD -> "empty card"
        PredicateKind.CREATED_BEFORE -> "created before ${p.before}"
        PredicateKind.SOURCE_IS -> "source is ${p.source}"
        PredicateKind.NEVER_CONTACTED -> "never contacted"
    }

    private fun textValues(field: TextField, c: Contact): List<String> = when (field) {
        TextField.EMAIL -> c.emails
        TextField.NAME -> listOfNotNull(c.name.formatted, c.name.given, c.name.family)
        TextField.ORG -> listOfNotNull(c.org)
        TextField.ADDRESS -> c.addresses
        TextField.URL -> c.urls
        TextField.NOTES -> listOfNotNull(c.notes)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.ConditionEvaluatorTest"`
Expected: PASS (all 5).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluator.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/ConditionEvaluatorTest.kt
git commit -m "feat(rules): add condition evaluator with AND/OR/NOT and reasons (#6)"
```

---

### Task 7: Rule engine and applyDeletions

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleEngine.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleEngineTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleEngineTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class RuleEngineTest {
    @Test fun `flags matching contacts with reasons, omits non-matching`() {
        val indeed = contact("1", emails = listOf("bob@indeed.com"))
        val keep = contact("2", emails = listOf("bob@personal.com"))
        val ruleSet = RuleSet(listOf(Rule("old job", TextMatch(TextField.EMAIL, "*@indeed.com"))))

        val flagged = RuleEngine.evaluate(listOf(indeed, keep), ruleSet)

        assertEquals(listOf("1"), flagged.map { it.contact.id })
        assertEquals("old job", flagged.single().matches.single().ruleName)
    }

    @Test fun `a contact hit by multiple rules appears once with multiple matches`() {
        val c = contact("1", emails = listOf("bob@indeed.com"))
        val ruleSet = RuleSet(listOf(
            Rule("indeed", TextMatch(TextField.EMAIL, "*@indeed.com")),
            Rule("any email", TextMatch(TextField.EMAIL, "*")),
        ))
        val flagged = RuleEngine.evaluate(listOf(c), ruleSet)
        assertEquals(1, flagged.size)
        assertEquals(2, flagged.single().matches.size)
    }

    @Test fun `applyDeletions removes approved ids and preserves order`() {
        val a = contact("a"); val b = contact("b"); val c = contact("c")
        assertEquals(listOf("a", "c"), applyDeletions(listOf(a, b, c), setOf("b")).map { it.id })
    }

    private fun contact(id: String, emails: List<String> = emptyList()) = Contact(
        id = id, source = Source.APPLE, name = ContactName(given = "X"),
        emails = emails, rawVCard = "",
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.RuleEngineTest"`
Expected: FAIL — `RuleEngine` / `applyDeletions` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleEngine.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact

/** Evaluates a RuleSet over contacts, returning the flagged contacts with per-rule reasons. */
object RuleEngine {
    private val evaluator = ConditionEvaluator()

    fun evaluate(contacts: List<Contact>, ruleSet: RuleSet): List<Flagged> =
        contacts.mapNotNull { contact ->
            val matches = ruleSet.rules.mapNotNull { rule ->
                evaluator.matchReason(rule.condition, contact)?.let { RuleMatch(rule.name, it) }
            }
            if (matches.isNotEmpty()) Flagged(contact, matches) else null
        }
}

/** Returns [contacts] with the approved ids removed, preserving order. */
fun applyDeletions(contacts: List<Contact>, approvedIds: Set<String>): List<Contact> =
    contacts.filterNot { it.id in approvedIds }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.RuleEngineTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleEngine.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleEngineTest.kt
git commit -m "feat(rules): add rule engine and applyDeletions (#6)"
```

---

### Task 8: JSON serialization (RuleStore)

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt` (add serialization annotations)
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/model/Contact.kt` (annotate `Source` if needed)
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/Serializers.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleStoreTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleStoreTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuleStoreTest {
    private val sample = RuleSet(listOf(
        Rule("old job", TextMatch(TextField.EMAIL, "*@indeed.com")),
        Rule("austin", PhoneMatch("512-???-????")),
        Rule("complex", And(listOf(
            Or(listOf(Predicate(PredicateKind.NO_EMAIL), Predicate(PredicateKind.SOURCE_IS, source = Source.GOOGLE))),
            Not(Predicate(PredicateKind.CREATED_BEFORE, before = Instant.parse("2015-01-01T00:00:00Z"))),
        ))),
    ))

    @Test fun `round-trips through json`() {
        val text = RuleStore.toJson(sample)
        assertEquals(sample, RuleStore.fromJson(text))
    }

    @Test fun `discriminator is type`() {
        assertEquals(true, RuleStore.toJson(sample).contains("\"type\": \"text\""))
    }

    @Test fun `malformed json throws`() {
        assertFailsWith<Exception> { RuleStore.fromJson("{ not valid") }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.RuleStoreTest"`
Expected: FAIL — `RuleStore` unresolved (and types not yet serializable).

- [ ] **Step 3: Annotate the types for serialization**

Edit `src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt` — add `@Serializable` to every type in the `Condition` hierarchy plus `Rule`/`RuleSet`, with `@SerialName` discriminators on the subtypes, and apply the Instant serializer to `Predicate.before`. Replace the file body with:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

enum class TextField { EMAIL, NAME, ORG, ADDRESS, URL, NOTES }

enum class PredicateKind {
    NO_NAME_AND_NO_PHONE, NO_EMAIL, EMPTY_CARD, CREATED_BEFORE, SOURCE_IS, NEVER_CONTACTED
}

@Serializable
sealed interface Condition

@Serializable
@SerialName("text")
data class TextMatch(val field: TextField, val glob: String) : Condition

@Serializable
@SerialName("phone")
data class PhoneMatch(val pattern: String) : Condition

@Serializable
@SerialName("predicate")
data class Predicate(
    val kind: PredicateKind,
    @Serializable(with = InstantIso8601Serializer::class) val before: Instant? = null,
    val source: Source? = null,
) : Condition

@Serializable
@SerialName("and")
data class And(val of: List<Condition>) : Condition

@Serializable
@SerialName("or")
data class Or(val of: List<Condition>) : Condition

@Serializable
@SerialName("not")
data class Not(val of: Condition) : Condition

@Serializable
data class Rule(val name: String, val condition: Condition)

@Serializable
data class RuleSet(val rules: List<Rule>) {
    companion object
}

data class RuleMatch(val ruleName: String, val reason: String)
data class Flagged(val contact: Contact, val matches: List<RuleMatch>)
```

If compilation complains that `Source` has no serializer, add `@Serializable` to the `Source` enum in `model/Contact.kt` (add `import kotlinx.serialization.Serializable` and annotate `@Serializable enum class Source { ... }`). Leave the rest of the model unchanged.

- [ ] **Step 4: Add the Instant serializer**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/Serializers.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/** Serializes java.time.Instant as an ISO-8601 string. */
object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
```

- [ ] **Step 5: Add the RuleStore**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleStore.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Loads and saves rule sets as pretty-printed JSON with a `type` discriminator. */
object RuleStore {
    private val json = Json {
        prettyPrint = true
        classDiscriminator = "type"
        encodeDefaults = false
    }

    fun toJson(ruleSet: RuleSet): String = json.encodeToString(RuleSet.serializer(), ruleSet)
    fun fromJson(text: String): RuleSet = json.decodeFromString(RuleSet.serializer(), text)
    fun load(path: Path): RuleSet = fromJson(path.readText())
    fun save(path: Path, ruleSet: RuleSet) = path.writeText(toJson(ruleSet))
}
```

- [ ] **Step 6: Run the test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.RuleStoreTest"`
Expected: PASS (all 3). The `discriminator is type` test asserts pretty-printed output contains `"type": "text"`; if the pretty-print spacing differs in this kotlinx version, adjust the assertion to match the real output (do not change the discriminator).

- [ ] **Step 7: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleTypes.kt src/main/kotlin/com/robsartin/contactotomy/core/rules/Serializers.kt src/main/kotlin/com/robsartin/contactotomy/core/rules/RuleStore.kt src/main/kotlin/com/robsartin/contactotomy/core/model/Contact.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/RuleStoreTest.kt
git commit -m "feat(rules): JSON serialization for rule sets (#6)"
```

---

### Task 9: Starter rules

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/rules/StarterRules.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/rules/StarterRulesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/rules/StarterRulesTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StarterRulesTest {
    @Test fun `starter set contains the seed rules and round-trips`() {
        val starter = RuleSet.starter()
        assertEquals(4, starter.rules.size)
        assertEquals(starter, RuleStore.fromJson(RuleStore.toJson(starter)))
    }

    @Test fun `starter flags an indeed address`() {
        val c = Contact(id = "1", source = Source.APPLE, name = ContactName(given = "Al"),
            emails = listOf("al@indeed.com"), rawVCard = "")
        val flagged = RuleEngine.evaluate(listOf(c), RuleSet.starter())
        assertTrue(flagged.single().matches.any { it.reason.contains("@indeed.com") })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.StarterRulesTest"`
Expected: FAIL — `RuleSet.starter` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/rules/StarterRules.kt`:

```kotlin
package com.robsartin.contactotomy.core.rules

/** The default seed rules, derived from the user's examples. Keep, edit, or delete. */
fun RuleSet.Companion.starter(): RuleSet = RuleSet(
    listOf(
        Rule("old job (indeed)", TextMatch(TextField.EMAIL, "*@indeed.com")),
        Rule("my own addresses", TextMatch(TextField.EMAIL, "sartin@*")),
        Rule("austin area code", PhoneMatch("512-???-????")),
        Rule("no name and no phone", Predicate(PredicateKind.NO_NAME_AND_NO_PHONE)),
    ),
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.rules.StarterRulesTest"`
Expected: PASS (both).

- [ ] **Step 5: Run the full gate end-to-end**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL — all Plan 1 + Plan 2 + Plan 3 tests, coverage floors, formatting, and Konsist green.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/rules/StarterRules.kt src/test/kotlin/com/robsartin/contactotomy/core/rules/StarterRulesTest.kt
git commit -m "feat(rules): add starter rule set (#6)"
```

---

## Self-Review

**Spec coverage:**
- §3 types (Condition AST, Rule, RuleSet, RuleMatch, Flagged): Task 2 (+ annotations Task 8). ✓
- §4.1 glob: Task 3. §4.2 phone national-number + suffix fallback: Task 4. §4.3 predicates incl. CREATED_BEFORE null→false and NEVER_CONTACTED stub: Task 5. §4.4 AND/OR/NOT incl. empty-And true / empty-Or false: Task 6. §4.5 reasons (leaf naming, OR satisfied-branch-only, NOT): Task 6. ✓
- §5 serialization (kotlinx, `type` discriminator, Instant ISO-8601, load/save, malformed throws): Tasks 1, 8. ✓
- §6 starter rules: Task 9. ✓
- §7 engine + applyDeletions: Task 7. ✓
- §8 testing: covered across Tasks 3–9. ✓
- §9 scope (no UI, no pipeline wiring, no usage signals): nothing added beyond the `rules` package; Konsist still guards no-UI. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. The Kover-exclusion (Task 1) and pretty-print-assertion (Task 8) notes are version-compat instructions, not missing logic.

**Type consistency:** `Condition` subtypes (`TextMatch`/`PhoneMatch`/`Predicate`/`And`/`Or`/`Not`) and their fields are identical across Tasks 2, 6, 8. `Predicate(kind, before, source)` signature matches its use in Tasks 5, 6, 8, 9. `RuleEngine.evaluate(List<Contact>, RuleSet): List<Flagged>` and `applyDeletions(List<Contact>, Set<String>): List<Contact>` are consistent across Tasks 7, 9. `RuleStore.toJson/fromJson/load/save` consistent across Tasks 8, 9. `RuleSet.starter()` (companion extension) matches the `companion object` declared in Task 2.

**Gate note for executor:** Each task runs `./gradlew check` before committing, so coverage/format/Konsist stay green throughout. Task 1's Kover exclusion of generated `$serializer` classes is what keeps branch coverage above the floor once `@Serializable` is added in Task 8.

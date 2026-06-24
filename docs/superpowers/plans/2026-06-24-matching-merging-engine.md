# Matching & Merging Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure-Kotlin engine that clusters duplicate contacts (name-gated, transitive on strong links only), proposes merged cards with provenance/conflicts, and applies the user's accept/reject/field decisions into a final deduplicated list.

**Architecture:** Three new packages under `com.robsartin.contactotomy.core` — `matcher`, `merger`, `apply` — all pure logic with no UI/Compose imports (Konsist-enforced). Builds on Plan 1's `Contact`/`ContactName`/`Source` model. See `docs/superpowers/specs/2026-06-24-matching-merging-design.md` and ADR-0005/0008.

**Tech Stack:** Kotlin (JVM, toolchain 21), JUnit 5, `./gradlew` (Gradle 8.13, auto-provisions JDK 21). No new dependencies.

---

## Conventions for every task

- Build/test with `./gradlew` (NEVER system `gradle`).
- Strict TDD: write the failing test, run it, see it fail, implement minimally, run it, see it pass, then commit.
- After each task run the FULL suite once (`./gradlew test`) to confirm the Konsist boundary test and prior tests still pass.
- No UI/Compose imports anywhere in `core`.

Existing model (Plan 1), for reference — do not modify:

```kotlin
// com.robsartin.contactotomy.core.model
enum class Source { APPLE, GOOGLE, FILE }
data class ContactName(val prefix: String? = null, val given: String? = null, val middle: String? = null,
                       val family: String? = null, val suffix: String? = null, val formatted: String? = null)
data class Contact(
    val id: String, val source: Source, val name: ContactName,
    val phones: List<String> = emptyList(), val rawPhones: List<String> = emptyList(),
    val emails: List<String> = emptyList(), val addresses: List<String> = emptyList(),
    val org: String? = null, val title: String? = null, val urls: List<String> = emptyList(),
    val notes: String? = null, val categories: List<String> = emptyList(),
    val createdAt: java.time.Instant? = null, val modifiedAt: java.time.Instant? = null, val rawVCard: String)
```

A reusable test helper (copy into the first test that needs it; later tests may redefine a local `contact(...)` factory):

```kotlin
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant

fun contact(
    id: String,
    given: String? = null, middle: String? = null, family: String? = null,
    phones: List<String> = emptyList(), emails: List<String> = emptyList(),
    org: String? = null, title: String? = null, notes: String? = null,
    categories: List<String> = emptyList(),
    modifiedAt: Instant? = null, createdAt: Instant? = null,
    source: Source = Source.APPLE,
) = Contact(
    id = id, source = source,
    name = ContactName(given = given, middle = middle, family = family),
    phones = phones, rawPhones = phones, emails = emails, org = org, title = title,
    notes = notes, categories = categories, modifiedAt = modifiedAt, createdAt = createdAt,
    rawVCard = "BEGIN:VCARD\nFN:${given ?: ""} ${family ?: ""}\nEND:VCARD",
)
```

## File Structure

- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypes.kt` — `Confidence`, `MatchReason`, `MatchEdge`, `Cluster`, `MatchResult`.
- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionary.kt` — nickname equivalence; loads `/nicknames.csv`.
- `src/main/resources/nicknames.csv` — curated nickname groups.
- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcher.kt` — given/family compatibility.
- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifier.kt` — classify a pair → `MatchEdge?`.
- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndex.kt` — blocking / candidate pairs.
- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/UnionFind.kt` — clustering helper.
- `src/main/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcher.kt` — orchestrates → `MatchResult`.
- `src/main/kotlin/com/robsartin/contactotomy/core/merger/MergeTypes.kt` — `FieldProvenance`, `ConflictCandidate`, `FieldConflict`, `MergeProposal`.
- `src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt` — cluster → `MergeProposal`.
- `src/main/kotlin/com/robsartin/contactotomy/core/apply/ApplyTypes.kt` — `ExcludedValue`, `Action`, `MergeDecision`.
- `src/main/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplier.kt` — `applyDecisions(...)`.
- Tests mirror these under `src/test/kotlin/...`.

---

### Task 1: Match types

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypes.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypesTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchTypesTest {
    private fun c(id: String) = Contact(id = id, source = Source.APPLE, name = ContactName(given = id), rawVCard = "")

    @Test
    fun `match edge carries confidence and reasons`() {
        val edge = MatchEdge(c("a"), c("b"), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE, MatchReason.NAME_EXACT))
        assertEquals(Confidence.HIGH, edge.confidence)
        assertEquals(listOf(MatchReason.SHARED_PHONE, MatchReason.NAME_EXACT), edge.reasons)
    }

    @Test
    fun `match result holds clusters and uncertain pairs`() {
        val cluster = Cluster("cluster-a+b", listOf(c("a"), c("b")), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))
        val result = MatchResult(clusters = listOf(cluster), uncertainPairs = emptyList())
        assertEquals(1, result.clusters.size)
        assertEquals("cluster-a+b", result.clusters.first().id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.MatchTypesTest"`
Expected: FAIL — `Confidence`/`MatchReason`/`MatchEdge`/`Cluster`/`MatchResult` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypes.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

enum class Confidence { HIGH, UNCERTAIN }

enum class MatchReason {
    SHARED_PHONE, SHARED_EMAIL,
    NAME_EXACT, NAME_NICKNAME, NAME_DROPPED_MIDDLE, NAME_INITIAL,
    SURNAME_CHANGE, NAME_ONLY,
}

data class MatchEdge(
    val a: Contact,
    val b: Contact,
    val confidence: Confidence,
    val reasons: List<MatchReason>,
)

data class Cluster(
    val id: String,
    val members: List<Contact>,
    val confidence: Confidence,
    val reasons: List<MatchReason>,
)

data class MatchResult(
    val clusters: List<Cluster>,
    val uncertainPairs: List<MatchEdge>,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.MatchTypesTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypes.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/MatchTypesTest.kt
git commit -m "feat: add matcher core types"
```

---

### Task 2: Nickname dictionary

**Files:**
- Create: `src/main/resources/nicknames.csv`
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionary.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionaryTest.kt`

- [ ] **Step 1: Create the curated nickname resource**

`src/main/resources/nicknames.csv` (comma-separated equivalence groups, one group per line; `#` lines are comments). This is a modest public-domain-style starter list, expandable later:

```
# Each line is a group of interchangeable first-name forms (lowercase).
robert,rob,robbie,bob,bobby,bert
william,will,bill,billy,willy,liam
richard,rich,rick,ricky,dick
james,jim,jimmy,jamie
john,johnny,jack
michael,mike,mikey,mick
charles,charlie,chuck,chas
thomas,tom,tommy
edward,ed,eddie,ted,teddy
joseph,joe,joey
daniel,dan,danny
matthew,matt
anthony,tony
christopher,chris
nicholas,nick,nicky
margaret,maggie,meg,peggy,marge
elizabeth,liz,lizzy,beth,betty,eliza,libby
katherine,catherine,kate,katie,kathy,cathy,kit
jennifer,jen,jenny
patricia,pat,patty,trish
deborah,deb,debbie
susan,sue,susie
jessica,jess,jessie
samuel,sam,sammy
samantha,sam,sammy
benjamin,ben,benji
alexander,alex,al,xander
alexandra,alex,sandra,sandy
```

- [ ] **Step 2: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionaryTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NicknameDictionaryTest {
    @Test
    fun `equivalent within a group, case insensitive`() {
        val dict = NicknameDictionary(listOf(setOf("robert", "rob", "bob")))
        assertTrue(dict.areEquivalent("Bob", "Robert"))
        assertTrue(dict.areEquivalent("rob", "bob"))
    }

    @Test
    fun `same string is always equivalent`() {
        val dict = NicknameDictionary(emptyList())
        assertTrue(dict.areEquivalent("Alice", "alice"))
    }

    @Test
    fun `different groups or unknown names are not equivalent`() {
        val dict = NicknameDictionary(listOf(setOf("robert", "bob"), setOf("william", "bill")))
        assertFalse(dict.areEquivalent("bob", "bill"))
        assertFalse(dict.areEquivalent("zaphod", "robert"))
    }

    @Test
    fun `loads bundled resource`() {
        val dict = NicknameDictionary.fromResource()
        assertTrue(dict.areEquivalent("bob", "robert"))
        assertTrue(dict.areEquivalent("liz", "elizabeth"))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.NicknameDictionaryTest"`
Expected: FAIL — `NicknameDictionary` unresolved.

- [ ] **Step 4: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionary.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

class NicknameDictionary(groups: List<Set<String>>) {
    private val groupOf: Map<String, Int> = buildMap {
        groups.forEachIndexed { index, group ->
            group.forEach { put(it.lowercase(), index) }
        }
    }

    /** True if both names are the same, or belong to the same nickname group. */
    fun areEquivalent(a: String, b: String): Boolean {
        val x = a.lowercase()
        val y = b.lowercase()
        if (x == y) return true
        val gx = groupOf[x] ?: return false
        val gy = groupOf[y] ?: return false
        return gx == gy
    }

    companion object {
        fun fromResource(path: String = "/nicknames.csv"): NicknameDictionary {
            val text = NicknameDictionary::class.java.getResourceAsStream(path)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("nickname resource not found: $path")
            val groups = text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { line -> line.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet() }
                .filter { it.size >= 2 }
                .toList()
            return NicknameDictionary(groups)
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.NicknameDictionaryTest"`
Expected: PASS (all 4).

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/nicknames.csv src/main/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionary.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/NicknameDictionaryTest.kt
git commit -m "feat: add nickname dictionary with bundled list"
```

---

### Task 3: Name matcher

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcher.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcherTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcherTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.ContactName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NameMatcherTest {
    private val matcher = NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob"))))
    private fun name(given: String? = null, middle: String? = null, family: String? = null) =
        ContactName(given = given, middle = middle, family = family)

    @Test
    fun `exact given names match`() {
        assertEquals(MatchReason.NAME_EXACT, matcher.givenMatch(name(given = "Robert"), name(given = "robert")))
    }

    @Test
    fun `nickname given names match`() {
        assertEquals(MatchReason.NAME_NICKNAME, matcher.givenMatch(name(given = "Bob"), name(given = "Robert")))
    }

    @Test
    fun `initial matches full given name`() {
        assertEquals(MatchReason.NAME_INITIAL, matcher.givenMatch(name(given = "R."), name(given = "Robert")))
    }

    @Test
    fun `clearly different given names do not match and conflict`() {
        assertNull(matcher.givenMatch(name(given = "Alice"), name(given = "Bob")))
        assertTrue(matcher.givenConflict(name(given = "Alice"), name(given = "Bob")))
    }

    @Test
    fun `missing given name is never a conflict`() {
        assertFalse(matcher.givenConflict(name(family = "Smith"), name(given = "Bob", family = "Smith")))
    }

    @Test
    fun `family matches when equal or one missing, differs when both present and unequal`() {
        assertTrue(matcher.familyMatches(name(family = "Sartin"), name(family = "sartin")))
        assertTrue(matcher.familyMatches(name(family = "Sartin"), name(given = "Rob")))
        assertFalse(matcher.familyMatches(name(family = "Doe"), name(family = "Smith")))
        assertTrue(matcher.familyDiffers(name(family = "Doe"), name(family = "Smith")))
        assertFalse(matcher.familyDiffers(name(family = "Doe"), name(given = "Jane")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.NameMatcherTest"`
Expected: FAIL — `NameMatcher` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcher.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.ContactName

/** Compares names for identity, allowing nicknames, initials, and dropped middles. */
class NameMatcher(private val nicknames: NicknameDictionary) {

    /** A positive given-name match reason, or null if there is no positive match. */
    fun givenMatch(a: ContactName, b: ContactName): MatchReason? {
        val x = norm(a.given)
        val y = norm(b.given)
        if (x.isEmpty() || y.isEmpty()) return null
        if (x == y) return MatchReason.NAME_EXACT
        if (nicknames.areEquivalent(x, y)) return MatchReason.NAME_NICKNAME
        if (initialCompatible(x, y)) return MatchReason.NAME_INITIAL
        return null
    }

    /** True only when both given names are present and clearly NOT the same person. */
    fun givenConflict(a: ContactName, b: ContactName): Boolean {
        val x = norm(a.given)
        val y = norm(b.given)
        if (x.isEmpty() || y.isEmpty()) return false
        return givenMatch(a, b) == null
    }

    /** Family names are compatible when equal, or when at least one is missing. */
    fun familyMatches(a: ContactName, b: ContactName): Boolean {
        val x = norm(a.family)
        val y = norm(b.family)
        if (x.isEmpty() || y.isEmpty()) return true
        return x == y
    }

    /** True when both family names are present and unequal (a possible surname change). */
    fun familyDiffers(a: ContactName, b: ContactName): Boolean {
        val x = norm(a.family)
        val y = norm(b.family)
        return x.isNotEmpty() && y.isNotEmpty() && x != y
    }

    private fun initialCompatible(x: String, y: String): Boolean {
        if (x.length == 1 && y.isNotEmpty()) return x[0] == y[0]
        if (y.length == 1 && x.isNotEmpty()) return y[0] == x[0]
        return false
    }

    private fun norm(s: String?): String =
        s?.lowercase()?.replace(".", "")?.replace(Regex("\\s+"), " ")?.trim() ?: ""
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.NameMatcherTest"`
Expected: PASS (all 6).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcher.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/NameMatcherTest.kt
git commit -m "feat: add name matcher (nickname/initial/dropped-middle aware)"
```

---

### Task 4: Edge classifier

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifier.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifierTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifierTest.kt` (uses the `contact(...)` helper from the Conventions section — paste it at the bottom of this test file):

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EdgeClassifierTest {
    private val classifier = EdgeClassifier(NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob")))))

    @Test
    fun `married couple sharing a phone is never merged`() {
        val alice = contact("a", given = "Alice", family = "Smith", phones = listOf("+15125550000"))
        val bob = contact("b", given = "Bob", family = "Smith", phones = listOf("+15125550000"))
        assertNull(classifier.classify(alice, bob))
    }

    @Test
    fun `nickname plus shared phone is HIGH`() {
        val a = contact("a", given = "Bob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val edge = classifier.classify(a, b)!!
        assertEquals(Confidence.HIGH, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.SHARED_PHONE))
        assertTrue(edge.reasons.contains(MatchReason.NAME_NICKNAME))
    }

    @Test
    fun `dropped middle plus shared phone is HIGH`() {
        val a = contact("a", given = "Robert", middle = "A", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        assertEquals(Confidence.HIGH, classifier.classify(a, b)!!.confidence)
    }

    @Test
    fun `surname change with shared email is HIGH and flagged`() {
        val a = contact("a", given = "Jane", family = "Doe", emails = listOf("jane@x.com"))
        val b = contact("b", given = "Jane", family = "Smith", emails = listOf("jane@x.com"))
        val edge = classifier.classify(a, b)!!
        assertEquals(Confidence.HIGH, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.SURNAME_CHANGE))
    }

    @Test
    fun `name only match with no shared contact is UNCERTAIN`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val edge = classifier.classify(a, b)!!
        assertEquals(Confidence.UNCERTAIN, edge.confidence)
        assertTrue(edge.reasons.contains(MatchReason.NAME_ONLY))
    }

    @Test
    fun `shared phone with one missing given name is UNCERTAIN`() {
        val a = contact("a", phones = listOf("+15125559999"))
        val b = contact("b", given = "Bob", family = "Sartin", phones = listOf("+15125559999"))
        assertEquals(Confidence.UNCERTAIN, classifier.classify(a, b)!!.confidence)
    }

    @Test
    fun `same given different family with no shared contact is not an edge`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Smith")
        assertNull(classifier.classify(a, b))
    }
}

fun contact(
    id: String,
    given: String? = null, middle: String? = null, family: String? = null,
    phones: List<String> = emptyList(), emails: List<String> = emptyList(),
    org: String? = null, title: String? = null, notes: String? = null,
    categories: List<String> = emptyList(),
    modifiedAt: java.time.Instant? = null, createdAt: java.time.Instant? = null,
    source: Source = Source.APPLE,
) = Contact(
    id = id, source = source,
    name = ContactName(given = given, middle = middle, family = family),
    phones = phones, rawPhones = phones, emails = emails, org = org, title = title,
    notes = notes, categories = categories, modifiedAt = modifiedAt, createdAt = createdAt,
    rawVCard = "BEGIN:VCARD\nFN:${given ?: ""} ${family ?: ""}\nEND:VCARD",
)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.EdgeClassifierTest"`
Expected: FAIL — `EdgeClassifier` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifier.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

/** Classifies a pair of contacts into a MatchEdge, or null when they must never merge. */
class EdgeClassifier(private val nameMatcher: NameMatcher) {

    fun classify(a: Contact, b: Contact): MatchEdge? {
        // Rule 1: clearly different given names => never merge, even with shared contact info.
        if (nameMatcher.givenConflict(a.name, b.name)) return null

        val sharedPhone = a.phones.any { it in b.phones }
        val sharedEmail = a.emails.any { it in b.emails }
        val hasSharedContact = sharedPhone || sharedEmail
        val givenReason = nameMatcher.givenMatch(a.name, b.name)

        val reasons = mutableListOf<MatchReason>()
        if (sharedPhone) reasons += MatchReason.SHARED_PHONE
        if (sharedEmail) reasons += MatchReason.SHARED_EMAIL

        // Rule 2: positive name match + shared contact info => HIGH (surname change flagged).
        if (hasSharedContact && givenReason != null) {
            reasons += givenReason
            if (nameMatcher.familyDiffers(a.name, b.name)) reasons += MatchReason.SURNAME_CHANGE
            return MatchEdge(a, b, Confidence.HIGH, reasons)
        }

        // Rule 3: name-only (given matches, family compatible) without shared contact => UNCERTAIN.
        if (!hasSharedContact && givenReason != null && nameMatcher.familyMatches(a.name, b.name)) {
            reasons += givenReason
            reasons += MatchReason.NAME_ONLY
            return MatchEdge(a, b, Confidence.UNCERTAIN, reasons)
        }

        // Rule 5: shared contact but indeterminate name (no positive match, no conflict) => UNCERTAIN.
        if (hasSharedContact && givenReason == null) {
            reasons += MatchReason.NAME_ONLY
            return MatchEdge(a, b, Confidence.UNCERTAIN, reasons)
        }

        return null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.EdgeClassifierTest"`
Expected: PASS (all 7).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifier.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/EdgeClassifierTest.kt
git commit -m "feat: add edge classifier (name-gated, shared-phone anti-merge)"
```

---

### Task 5: Candidate index (blocking)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndex.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndexTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndexTest.kt` (paste the `contact(...)` helper at the bottom, as in Task 4):

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CandidateIndexTest {
    private fun idsOf(pairs: Set<Pair<Contact, Contact>>) =
        pairs.map { setOf(it.first.id, it.second.id) }.toSet()

    @Test
    fun `cards sharing a phone are candidates`() {
        val a = contact("a", given = "Rob", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", phones = listOf("+15125551234"))
        val c = contact("c", given = "Zoe", phones = listOf("+15125559999"))
        val pairs = CandidateIndex(listOf(a, b, c)).candidatePairs()
        assertTrue(idsOf(pairs).contains(setOf("a", "b")))
        assertEquals(1, pairs.size)
    }

    @Test
    fun `cards sharing only a family name are candidates`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Janet", family = "Doe")
        val pairs = CandidateIndex(listOf(a, b)).candidatePairs()
        assertEquals(setOf(setOf("a", "b")), idsOf(pairs))
    }

    @Test
    fun `a pair sharing multiple keys appears only once`() {
        val a = contact("a", given = "Jane", family = "Doe", phones = listOf("+15125551234"), emails = listOf("j@x.com"))
        val b = contact("b", given = "Jane", family = "Doe", phones = listOf("+15125551234"), emails = listOf("j@x.com"))
        val pairs = CandidateIndex(listOf(a, b)).candidatePairs()
        assertEquals(1, pairs.size)
    }

    @Test
    fun `cards sharing nothing are not candidates`() {
        val a = contact("a", given = "Jane", family = "Doe", phones = listOf("+1"))
        val b = contact("b", given = "Mark", family = "Twain", phones = listOf("+2"))
        assertTrue(CandidateIndex(listOf(a, b)).candidatePairs().isEmpty())
    }
}

// paste the contact(...) helper here (identical to Task 4)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.CandidateIndexTest"`
Expected: FAIL — `CandidateIndex` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndex.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

/** Generates candidate pairs by blocking on shared phone, email, or family name. */
class CandidateIndex(private val contacts: List<Contact>) {

    fun candidatePairs(): Set<Pair<Contact, Contact>> {
        val buckets = HashMap<String, MutableList<Contact>>()
        fun add(key: String, c: Contact) {
            if (key.isBlank()) return
            buckets.getOrPut(key) { mutableListOf() }.add(c)
        }
        for (c in contacts) {
            c.phones.forEach { add("phone:$it", c) }
            c.emails.forEach { add("email:$it", c) }
            familyKey(c)?.let { add("family:$it", c) }
        }

        val pairs = LinkedHashSet<Pair<Contact, Contact>>()
        for (bucket in buckets.values) {
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    if (a.id != b.id) pairs += orderPair(a, b)
                }
            }
        }
        return pairs
    }

    private fun familyKey(c: Contact): String? =
        c.name.family?.lowercase()?.trim()?.ifEmpty { null }

    private fun orderPair(a: Contact, b: Contact): Pair<Contact, Contact> =
        if (a.id <= b.id) a to b else b to a
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.CandidateIndexTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndex.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/CandidateIndexTest.kt
git commit -m "feat: add candidate index (blocking by phone/email/family)"
```

---

### Task 6: Union-find and ContactMatcher

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/UnionFind.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcher.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/UnionFindTest.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcherTest.kt`

- [ ] **Step 1: Write the failing UnionFind test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/UnionFindTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import kotlin.test.Test
import kotlin.test.assertEquals

class UnionFindTest {
    @Test
    fun `unions form connected components`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("b", "c")
        val groups = uf.groups().values.map { it.toSet() }.toSet()
        assertEquals(setOf(setOf("a", "b", "c"), setOf("d")), groups)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.UnionFindTest"`
Expected: FAIL — `UnionFind` unresolved.

- [ ] **Step 3: Implement UnionFind**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/UnionFind.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

class UnionFind<T>(items: Collection<T>) {
    private val parent = HashMap<T, T>().apply { items.forEach { put(it, it) } }

    fun find(x: T): T {
        var root = x
        while (parent[root] != root) root = parent.getValue(root)
        var cur = x
        while (parent[cur] != root) {
            val next = parent.getValue(cur)
            parent[cur] = root
            cur = next
        }
        return root
    }

    fun union(a: T, b: T) {
        parent[find(a)] = find(b)
    }

    fun groups(): Map<T, List<T>> = parent.keys.groupBy { find(it) }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.UnionFindTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing ContactMatcher test**

`src/test/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcherTest.kt` (paste the `contact(...)` helper at the bottom):

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactMatcherTest {
    private val matcher = ContactMatcher(
        EdgeClassifier(NameMatcher(NicknameDictionary(listOf(setOf("robert", "rob", "bob")))))
    )

    @Test
    fun `transitive chain over HIGH edges forms one cluster`() {
        // a-b share a phone; b-c share an email; a and c share nothing directly.
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1111"), emails = listOf("r@x.com"))
        val c = contact("c", given = "Bob", family = "Sartin", emails = listOf("r@x.com"))
        val result = matcher.match(listOf(a, b, c))
        assertEquals(1, result.clusters.size)
        assertEquals(setOf("a", "b", "c"), result.clusters.first().members.map { it.id }.toSet())
    }

    @Test
    fun `uncertain name-only pair is not clustered but surfaced`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val result = matcher.match(listOf(a, b))
        assertTrue(result.clusters.isEmpty())
        assertEquals(1, result.uncertainPairs.size)
    }

    @Test
    fun `married couple sharing a phone yields no cluster and no uncertain pair`() {
        val alice = contact("a", given = "Alice", family = "Smith", phones = listOf("+1999"))
        val bob = contact("b", given = "Bob", family = "Smith", phones = listOf("+1999"))
        val result = matcher.match(listOf(alice, bob))
        assertTrue(result.clusters.isEmpty())
        assertTrue(result.uncertainPairs.isEmpty())
    }

    @Test
    fun `cluster id is deterministic from member ids`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1111"))
        assertEquals(
            matcher.match(listOf(a, b)).clusters.first().id,
            matcher.match(listOf(b, a)).clusters.first().id,
        )
    }
}

// paste the contact(...) helper here (identical to Task 4)
```

- [ ] **Step 6: Run it to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.ContactMatcherTest"`
Expected: FAIL — `ContactMatcher` unresolved.

- [ ] **Step 7: Implement ContactMatcher**

`src/main/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcher.kt`:

```kotlin
package com.robsartin.contactotomy.core.matcher

import com.robsartin.contactotomy.core.model.Contact

/** Turns a flat contact list into HIGH-confidence clusters plus UNCERTAIN review pairs. */
class ContactMatcher(
    private val classifier: EdgeClassifier,
    private val indexFactory: (List<Contact>) -> CandidateIndex = ::CandidateIndex,
) {
    fun match(contacts: List<Contact>): MatchResult {
        val edges = indexFactory(contacts).candidatePairs()
            .mapNotNull { (a, b) -> classifier.classify(a, b) }

        val highEdges = edges.filter { it.confidence == Confidence.HIGH }
        val uncertainEdges = edges.filter { it.confidence == Confidence.UNCERTAIN }

        val uf = UnionFind(contacts.map { it.id })
        highEdges.forEach { uf.union(it.a.id, it.b.id) }

        val byId = contacts.associateBy { it.id }
        val reasonsByRoot = highEdges.groupBy { uf.find(it.a.id) }

        val clusters = uf.groups()
            .filterValues { it.size >= 2 }
            .map { (root, ids) ->
                val members = ids.map { byId.getValue(it) }.sortedBy { it.id }
                val reasons = reasonsByRoot[root].orEmpty().flatMap { it.reasons }.distinct()
                Cluster(clusterId(members), members, Confidence.HIGH, reasons)
            }
            .sortedBy { it.id }

        val clusterIdByMember = clusters
            .flatMap { c -> c.members.map { it.id to c.id } }
            .toMap()

        // Drop uncertain pairs already unified inside one HIGH cluster.
        val uncertainPairs = uncertainEdges.filter {
            val ca = clusterIdByMember[it.a.id]
            val cb = clusterIdByMember[it.b.id]
            ca == null || cb == null || ca != cb
        }

        return MatchResult(clusters, uncertainPairs)
    }

    private fun clusterId(members: List<Contact>): String =
        "cluster-" + members.map { it.id }.sorted().joinToString("+")
}
```

- [ ] **Step 8: Run it to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.matcher.ContactMatcherTest"`
Expected: PASS (all 4).

- [ ] **Step 9: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/matcher/UnionFind.kt src/main/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcher.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/UnionFindTest.kt src/test/kotlin/com/robsartin/contactotomy/core/matcher/ContactMatcherTest.kt
git commit -m "feat: cluster contacts via union-find over HIGH edges"
```

---

### Task 7: Merge types and basic merge

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/merger/MergeTypes.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerTest.kt` (paste the `contact(...)` helper at the bottom):

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactMergerTest {
    private val merger = ContactMerger()

    private fun cluster(vararg members: Contact) =
        Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    @Test
    fun `unions multi-value fields and prefers newest for single-value`() {
        val older = contact("a", given = "Robert", family = "Sartin",
            phones = listOf("+1111"), emails = listOf("old@x.com"), categories = listOf("Work"),
            org = "OldCo", modifiedAt = Instant.parse("2020-01-01T00:00:00Z"))
        val newer = contact("b", given = "Robert", family = "Sartin",
            phones = listOf("+2222"), emails = listOf("new@x.com"), categories = listOf("Friends"),
            org = "NewCo", modifiedAt = Instant.parse("2024-01-01T00:00:00Z"))

        val merged = merger.merge(cluster(older, newer)).merged

        assertEquals(listOf("+2222", "+1111"), merged.phones)       // newer (primary) first
        assertEquals(setOf("old@x.com", "new@x.com"), merged.emails.toSet())
        assertEquals(setOf("Work", "Friends"), merged.categories.toSet())
        assertEquals("NewCo", merged.org)                           // newest non-null
    }

    @Test
    fun `merged id is deterministic from member ids`() {
        val a = contact("a", given = "Rob", family = "Sartin")
        val b = contact("b", given = "Rob", family = "Sartin")
        assertEquals("merged-a+b", merger.merge(cluster(a, b)).merged.id)
        assertEquals("merged-a+b", merger.merge(cluster(b, a)).merged.id)
    }
}

// paste the contact(...) helper here (identical to Task 4)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.merger.ContactMergerTest"`
Expected: FAIL — `ContactMerger`/`MergeProposal` unresolved.

- [ ] **Step 3: Write the merge types**

`src/main/kotlin/com/robsartin/contactotomy/core/merger/MergeTypes.kt`:

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import java.time.Instant

data class FieldProvenance(
    val field: String,
    val value: String,
    val sourceContactIds: List<String>,
)

data class ConflictCandidate(
    val value: String,
    val sourceContactId: String,
    val modifiedAt: Instant?,
)

data class FieldConflict(
    val field: String,
    val candidates: List<ConflictCandidate>,
    val chosen: String,
)

data class MergeProposal(
    val cluster: Cluster,
    val merged: Contact,
    val provenance: List<FieldProvenance>,
    val conflicts: List<FieldConflict>,
)
```

- [ ] **Step 4: Write the basic merger**

`src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt`:

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import java.time.Instant

/** Builds a MergeProposal for a cluster: newest-wins single values, unioned multi-values. */
class ContactMerger {

    fun merge(cluster: Cluster): MergeProposal {
        // Members ordered newest-first; ties broken by id for determinism.
        val ordered = cluster.members.sortedWith(
            compareByDescending<Contact> { it.modifiedAt ?: Instant.MIN }.thenBy { it.id }
        )
        val primary = ordered.first()

        val merged = Contact(
            id = mergedId(cluster),
            source = primary.source,
            name = primary.name,
            phones = union(ordered.map { it.phones }),
            rawPhones = union(ordered.map { it.rawPhones }),
            emails = union(ordered.map { it.emails }),
            addresses = union(ordered.map { it.addresses }),
            org = ordered.firstNotNullOfOrNull { it.org },
            title = ordered.firstNotNullOfOrNull { it.title },
            urls = union(ordered.map { it.urls }),
            notes = ordered.firstNotNullOfOrNull { it.notes },
            categories = union(ordered.map { it.categories }),
            createdAt = cluster.members.mapNotNull { it.createdAt }.minOrNull(),
            modifiedAt = primary.modifiedAt,
            rawVCard = primary.rawVCard,
        )

        return MergeProposal(cluster, merged, provenance = emptyList(), conflicts = emptyList())
    }

    private fun union(lists: List<List<String>>): List<String> {
        val out = LinkedHashSet<String>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    private fun mergedId(cluster: Cluster): String =
        "merged-" + cluster.members.map { it.id }.sorted().joinToString("+")
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.merger.ContactMergerTest"`
Expected: PASS (both tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/merger/MergeTypes.kt src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerTest.kt
git commit -m "feat: add merger producing merged card (union + newest-wins)"
```

---

### Task 8: Provenance, conflicts, and most-complete name

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerProvenanceTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerProvenanceTest.kt` (paste the `contact(...)` helper at the bottom):

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactMergerProvenanceTest {
    private val merger = ContactMerger()
    private fun cluster(vararg members: Contact) =
        Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    @Test
    fun `records a conflict for disagreeing single-value field with newest chosen`() {
        val older = contact("a", given = "Robert", family = "Sartin", org = "OldCo",
            modifiedAt = Instant.parse("2020-01-01T00:00:00Z"))
        val newer = contact("b", given = "Robert", family = "Sartin", org = "NewCo",
            modifiedAt = Instant.parse("2024-01-01T00:00:00Z"))

        val proposal = merger.merge(cluster(older, newer))
        val orgConflict = proposal.conflicts.single { it.field == "org" }
        assertEquals("NewCo", orgConflict.chosen)
        assertEquals(setOf("OldCo", "NewCo"), orgConflict.candidates.map { it.value }.toSet())
    }

    @Test
    fun `no conflict when single-value field agrees or only one present`() {
        val a = contact("a", given = "Robert", family = "Sartin", org = "Acme")
        val b = contact("b", given = "Robert", family = "Sartin", org = "Acme")
        assertTrue(merger.merge(cluster(a, b)).conflicts.none { it.field == "org" })
    }

    @Test
    fun `chooses the most complete name`() {
        val full = contact("a", given = "Robert", middle = "A", family = "Sartin",
            modifiedAt = Instant.parse("2020-01-01T00:00:00Z"))
        val sparse = contact("b", given = "Robert", family = "Sartin",
            modifiedAt = Instant.parse("2024-01-01T00:00:00Z"))    // newer but less complete
        val merged = merger.merge(cluster(full, sparse)).merged
        assertEquals("A", merged.name.middle)
    }

    @Test
    fun `provenance maps a phone value to its source contact`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Rob", family = "Sartin", phones = listOf("+2222"))
        val prov = merger.merge(cluster(a, b)).provenance
        val p1 = prov.single { it.field == "phones" && it.value == "+1111" }
        assertEquals(listOf("a"), p1.sourceContactIds)
    }
}

// paste the contact(...) helper here (identical to Task 4)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.merger.ContactMergerProvenanceTest"`
Expected: FAIL — proposal has empty `conflicts`/`provenance`, and the name is the newer sparse one (middle is null).

- [ ] **Step 3: Replace `ContactMerger.kt` with the enriched version**

`src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt`:

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import java.time.Instant

/** Builds a MergeProposal for a cluster, with provenance, conflicts, and most-complete name. */
class ContactMerger {

    fun merge(cluster: Cluster): MergeProposal {
        val ordered = cluster.members.sortedWith(
            compareByDescending<Contact> { it.modifiedAt ?: Instant.MIN }.thenBy { it.id }
        )
        val primary = ordered.first()
        val name = mostCompleteName(ordered)

        val phones = union(ordered.map { it.phones })
        val rawPhones = union(ordered.map { it.rawPhones })
        val emails = union(ordered.map { it.emails })
        val addresses = union(ordered.map { it.addresses })
        val urls = union(ordered.map { it.urls })
        val categories = union(ordered.map { it.categories })

        val org = ordered.firstNotNullOfOrNull { it.org }
        val title = ordered.firstNotNullOfOrNull { it.title }
        val notes = ordered.firstNotNullOfOrNull { it.notes }

        val merged = Contact(
            id = mergedId(cluster), source = primary.source, name = name,
            phones = phones, rawPhones = rawPhones, emails = emails, addresses = addresses,
            org = org, title = title, urls = urls, notes = notes, categories = categories,
            createdAt = cluster.members.mapNotNull { it.createdAt }.minOrNull(),
            modifiedAt = primary.modifiedAt, rawVCard = primary.rawVCard,
        )

        val provenance = buildList {
            addAll(multiProvenance("phones", phones, ordered) { it.phones })
            addAll(multiProvenance("emails", emails, ordered) { it.emails })
            addAll(multiProvenance("addresses", addresses, ordered) { it.addresses })
            addAll(multiProvenance("urls", urls, ordered) { it.urls })
            addAll(multiProvenance("categories", categories, ordered) { it.categories })
            org?.let { add(singleProvenance("org", it, ordered) { c -> c.org }) }
            title?.let { add(singleProvenance("title", it, ordered) { c -> c.title }) }
            notes?.let { add(singleProvenance("notes", it, ordered) { c -> c.notes }) }
        }

        val conflicts = listOfNotNull(
            conflictFor("org", ordered) { it.org },
            conflictFor("title", ordered) { it.title },
            conflictFor("notes", ordered) { it.notes },
        )

        return MergeProposal(cluster, merged, provenance, conflicts)
    }

    private fun mostCompleteName(ordered: List<Contact>): ContactName =
        ordered.maxByOrNull { completeness(it.name) }?.name ?: ordered.first().name

    private fun completeness(n: ContactName): Int =
        listOf(n.prefix, n.given, n.middle, n.family, n.suffix).count { !it.isNullOrBlank() }

    private fun multiProvenance(
        field: String, values: List<String>, members: List<Contact>, getter: (Contact) -> List<String>,
    ): List<FieldProvenance> = values.map { value ->
        FieldProvenance(field, value, members.filter { value in getter(it) }.map { it.id })
    }

    private fun singleProvenance(
        field: String, value: String, members: List<Contact>, getter: (Contact) -> String?,
    ): FieldProvenance =
        FieldProvenance(field, value, members.filter { getter(it) == value }.map { it.id })

    private fun conflictFor(
        field: String, ordered: List<Contact>, getter: (Contact) -> String?,
    ): FieldConflict? {
        val candidates = ordered.mapNotNull { c -> getter(c)?.let { ConflictCandidate(it, c.id, c.modifiedAt) } }
        if (candidates.map { it.value }.distinct().size < 2) return null
        return FieldConflict(field, candidates, chosen = candidates.first().value)
    }

    private fun union(lists: List<List<String>>): List<String> {
        val out = LinkedHashSet<String>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    private fun mergedId(cluster: Cluster): String =
        "merged-" + cluster.members.map { it.id }.sorted().joinToString("+")
}
```

- [ ] **Step 4: Run both merger tests to verify they pass**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.merger.*"`
Expected: PASS (Task 7 and Task 8 tests both green).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerProvenanceTest.kt
git commit -m "feat: add provenance, conflicts, and most-complete-name to merger"
```

---

### Task 9: Apply decisions

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/apply/ApplyTypes.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplier.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplierTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplierTest.kt` (paste the `contact(...)` helper at the bottom):

```kotlin
package com.robsartin.contactotomy.core.apply

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.core.merger.MergeProposal
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecisionApplierTest {
    private val applier = DecisionApplier()
    private val merger = ContactMerger()

    private fun proposalFor(vararg members: Contact): MergeProposal =
        merger.merge(Cluster(
            "cluster-" + members.map { it.id }.sorted().joinToString("+"),
            members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE),
        ))

    @Test
    fun `accept replaces members with merged card and keeps singletons`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val c = contact("c", given = "Zoe", family = "Quinn")
        val p = proposalFor(a, b)

        val result = applier.applyDecisions(
            listOf(a, b, c), listOf(p),
            listOf(MergeDecision(p.cluster.id, Action.ACCEPT)),
        )

        assertEquals(listOf(p.merged.id, "c"), result.map { it.id })
        assertEquals(setOf("+1111", "+2222"), result.first().phones.toSet())
    }

    @Test
    fun `no decision defaults to reject, leaving members intact`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val p = proposalFor(a, b)
        val result = applier.applyDecisions(listOf(a, b), listOf(p), emptyList())
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    @Test
    fun `excluded value is dropped from the merged card`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val p = proposalFor(a, b)
        val result = applier.applyDecisions(
            listOf(a, b), listOf(p),
            listOf(MergeDecision(p.cluster.id, Action.ACCEPT, excludedValues = setOf(ExcludedValue("phones", "+1111")))),
        )
        assertTrue("+1111" !in result.first().phones)
        assertTrue("+2222" in result.first().phones)
    }

    @Test
    fun `conflict choice overrides the merged single-value field`() {
        val a = contact("a", given = "Rob", family = "Sartin", org = "OldCo")
        val b = contact("b", given = "Robert", family = "Sartin", org = "NewCo")
        val p = proposalFor(a, b)
        val result = applier.applyDecisions(
            listOf(a, b), listOf(p),
            listOf(MergeDecision(p.cluster.id, Action.ACCEPT, conflictChoices = mapOf("org" to "OldCo"))),
        )
        assertEquals("OldCo", result.first().org)
    }

    @Test
    fun `output is deterministic`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1111"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+2222"))
        val p = proposalFor(a, b)
        val decisions = listOf(MergeDecision(p.cluster.id, Action.ACCEPT))
        assertEquals(
            applier.applyDecisions(listOf(a, b), listOf(p), decisions),
            applier.applyDecisions(listOf(a, b), listOf(p), decisions),
        )
    }
}

// paste the contact(...) helper here (identical to Task 4)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.apply.DecisionApplierTest"`
Expected: FAIL — `DecisionApplier`/`MergeDecision`/`Action`/`ExcludedValue` unresolved.

- [ ] **Step 3: Write the apply types**

`src/main/kotlin/com/robsartin/contactotomy/core/apply/ApplyTypes.kt`:

```kotlin
package com.robsartin.contactotomy.core.apply

enum class Action { ACCEPT, REJECT }

data class ExcludedValue(val field: String, val value: String)

data class MergeDecision(
    val clusterId: String,
    val action: Action,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
)
```

- [ ] **Step 4: Write the DecisionApplier**

`src/main/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplier.kt`:

```kotlin
package com.robsartin.contactotomy.core.apply

import com.robsartin.contactotomy.core.merger.MergeProposal
import com.robsartin.contactotomy.core.model.Contact

/** Applies accept/reject/field decisions to produce the final deduplicated contact list. */
class DecisionApplier {

    fun applyDecisions(
        allContacts: List<Contact>,
        proposals: List<MergeProposal>,
        decisions: List<MergeDecision>,
    ): List<Contact> {
        val decisionByCluster = decisions.associateBy { it.clusterId }
        val accepted = proposals.filter {
            (decisionByCluster[it.cluster.id]?.action ?: Action.REJECT) == Action.ACCEPT
        }

        val acceptedMemberIds = accepted.flatMap { it.cluster.members.map { m -> m.id } }.toSet()
        val indexOf = allContacts.withIndex().associate { (i, c) -> c.id to i }
        val mergedAtAnchor = HashMap<Int, Contact>()
        for (p in accepted) {
            val anchor = p.cluster.members.minOf { indexOf.getValue(it.id) }
            mergedAtAnchor[anchor] = adjust(p, decisionByCluster.getValue(p.cluster.id))
        }

        val result = ArrayList<Contact>()
        allContacts.forEachIndexed { i, c ->
            mergedAtAnchor[i]?.let { result.add(it) }
            if (c.id !in acceptedMemberIds) result.add(c)
        }
        return result
    }

    private fun adjust(proposal: MergeProposal, decision: MergeDecision): Contact {
        var merged = proposal.merged
        for ((field, value) in decision.conflictChoices) merged = setSingle(merged, field, value)
        for (excluded in decision.excludedValues) merged = removeValue(merged, excluded.field, excluded.value)
        return merged
    }

    private fun setSingle(c: Contact, field: String, value: String): Contact = when (field) {
        "org" -> c.copy(org = value)
        "title" -> c.copy(title = value)
        "notes" -> c.copy(notes = value)
        else -> c
    }

    private fun removeValue(c: Contact, field: String, value: String): Contact = when (field) {
        "phones" -> c.copy(phones = c.phones - value)
        "rawPhones" -> c.copy(rawPhones = c.rawPhones - value)
        "emails" -> c.copy(emails = c.emails - value)
        "addresses" -> c.copy(addresses = c.addresses - value)
        "urls" -> c.copy(urls = c.urls - value)
        "categories" -> c.copy(categories = c.categories - value)
        "org" -> if (c.org == value) c.copy(org = null) else c
        "title" -> if (c.title == value) c.copy(title = null) else c
        "notes" -> if (c.notes == value) c.copy(notes = null) else c
        else -> c
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.apply.DecisionApplierTest"`
Expected: PASS (all 5).

- [ ] **Step 6: Run the FULL suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all Plan 1 + Plan 2 tests pass, including the Konsist boundary test (which now also guards the new `matcher`/`merger`/`apply` packages).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/apply/ApplyTypes.kt src/main/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplier.kt src/test/kotlin/com/robsartin/contactotomy/core/apply/DecisionApplierTest.kt
git commit -m "feat: apply merge decisions into final contact list"
```

---

## Self-Review

**Spec coverage:**
- §3 modules (`matcher`/`merger`/`apply`): Tasks 1–9. ✓
- §4 core types (Confidence, MatchReason, MatchEdge, Cluster, MatchResult, provenance/conflict/proposal, apply types): Tasks 1, 7, 9. ✓
- §5.1 name compatibility (nickname/initial/dropped-middle/surname): Tasks 2, 3. ✓
- §5.2 edge rules 1–5 incl. anti-merge and indeterminate-name: Task 4 (one test per rule). ✓
- §5.3 blocking: Task 5. ✓
- §5.4 clustering, transitive-on-HIGH-only, uncertain-not-clustered, deterministic ids: Task 6. ✓
- §6 merging (primary newest, union multi-value, conflicts, most-complete name, provenance, deterministic merged id, createdAt earliest): Tasks 7, 8. ✓
- §7 apply (ACCEPT/REJECT, no-decision→REJECT, exclusions, conflict choices, singleton passthrough, determinism): Task 9. ✓
- §8 testing (couple/nickname/surname/transitive/name-only/conflict/apply/determinism): covered across Tasks 4, 6, 8, 9. ✓
- §9 scope (no UI/rules/persistence): nothing in the plan adds those; Konsist still enforces no-UI in `core`. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. The "paste the `contact(...)` helper" instruction references the concrete helper printed in full in the Conventions section and in Task 4.

**Type consistency:** `ContactMatcher(EdgeClassifier(NameMatcher(NicknameDictionary)))` construction is consistent across Task 6 and Task 9 tests. `ContactMerger.merge(Cluster): MergeProposal` signature is identical in Tasks 7, 8, 9. `MergeDecision`/`Action`/`ExcludedValue` field names match between Task 9 types and their use in tests. Field-name string keys (`"org"`, `"phones"`, …) used in `DecisionApplier` match those produced by `ContactMerger`'s provenance/conflicts. `Cluster.id`/`members`/`confidence`/`reasons` used consistently. `merged-<sorted ids>` and `cluster-<sorted ids>` id formats are consistent across merger and matcher.

**Note for executor:** Task 8 fully replaces the `ContactMerger.kt` written in Task 7 (Task 7's version returns empty provenance/conflicts and primary-name; Task 8's adds them). Both merger test files remain and must both pass after Task 8.

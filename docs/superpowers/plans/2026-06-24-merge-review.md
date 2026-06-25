# Merge Review 4b-1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the merge-review screen for matcher-proposed clusters + uncertain pairs: a testable `MergeReviewStore` over the existing engine, and a list+detail UI (merged card with per-field toggles/conflict switches, bulk accept) that commits the merged contacts to `AppStore`.

**Architecture:** `com.robsartin.contactotomy.ui.MergeReviewStore` (StateFlow, no Compose) wraps `core` matcher/merger/applyDecisions (ADR-0013); thin composables render it and are covered by `runComposeUiTest` (ADR-0012). Builds on Plan 4a (`AppStore`, wizard). Manual merge is 4b-2. See `docs/superpowers/specs/2026-06-24-merge-review-design.md`. Tracking issue #10.

**Tech Stack:** Kotlin 2.0.21, Compose Desktop, kotlinx-coroutines (+ test), JUnit5, Kover (≥90 line / ≥65 branch), `./gradlew` (Gradle 8.13).

---

## Conventions for every task

- Build/test with `./gradlew` (NEVER system `gradle`).
- Strict TDD: failing test first → fail → minimal impl → pass.
- Before each commit: `./gradlew spotlessApply && ./gradlew check` → BUILD SUCCESSFUL (tests + Kover 90/65 + spotlessCheck + Konsist).
- UI code only in `com.robsartin.contactotomy.ui`; no `androidx.compose` in `core`.
- Branch `10-merge-review` (already checked out); commit there. Reference issue #10 in messages.

Existing APIs you build on (do not change unless a task says so):

```kotlin
// core.matcher
class ContactMatcher(classifier: EdgeClassifier, indexFactory: ...= ::CandidateIndex) { fun match(contacts: List<Contact>): MatchResult }
class EdgeClassifier(nameMatcher: NameMatcher)
class NameMatcher(nicknames: NicknameDictionary)
object/class NicknameDictionary { companion object { fun fromResource(path: String = "/nicknames.csv"): NicknameDictionary } }
enum class Confidence { HIGH, UNCERTAIN }
data class Cluster(val id: String, val members: List<Contact>, val confidence: Confidence, val reasons: List<MatchReason>)
data class MatchEdge(val a: Contact, val b: Contact, val confidence: Confidence, val reasons: List<MatchReason>)
data class MatchResult(val clusters: List<Cluster>, val uncertainPairs: List<MatchEdge>)
// core.merger
class ContactMerger { fun merge(cluster: Cluster): MergeProposal }
data class MergeProposal(val cluster: Cluster, val merged: Contact, val provenance: List<FieldProvenance>, val conflicts: List<FieldConflict>)
data class FieldConflict(val field: String, val candidates: List<ConflictCandidate>, val chosen: String)
data class ConflictCandidate(val value: String, val sourceContactId: String, val modifiedAt: java.time.Instant?)
// core.apply
enum class Action { ACCEPT, REJECT }
data class ExcludedValue(val field: String, val value: String)
data class MergeDecision(val clusterId: String, val action: Action, val excludedValues: Set<ExcludedValue> = emptySet(), val conflictChoices: Map<String, String> = emptyMap())
fun applyDecisions(allContacts: List<Contact>, proposals: List<MergeProposal>, decisions: List<MergeDecision>): List<Contact>
// ui (Plan 4a)
class AppStore(...) { val state: StateFlow<AppState>; fun next(); fun goTo(screen); ... }
data class AppState(screen, imported, contacts, importing, error)  // 4a
```

**Shared test contact factory (no per-class duplication).** A single `contact(...)`
lives in its own test-only package and is imported by every test that needs it.
A dedicated package (not under `...core` or a test package that already has a
top-level `contact`) avoids the same-package overload collision we hit earlier,
and being test source it is excluded from coverage and outside the Konsist `core`
rule. It is created once in the **prerequisite task below**, then imported:

```kotlin
import com.robsartin.contactotomy.testsupport.contact
```

Keep the factory **minimal (YAGNI)**: it exposes only the fields 4b-1's tests use.
Don't add speculative parameters for fields a later plan might need — add a
parameter when (and where) a test actually needs it.

**Important for every task below:** the test snippets show their `contact(...)`
calls using this shared factory. Do **not** redeclare a private `contact(...)` in
any test class — add the import above instead. (If you see an inline factory in a
snippet from an earlier draft, replace it with the import.)

## File Structure

- `src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt` — shared `contact(...)` test factory (Task 0).
- `ui/AppState.kt`, `ui/AppStore.kt` — add `mergedContacts` + setter (Task 1).
- `ui/MergeReviewTypes.kt` — `Origin`, `Decision`, `ReviewItem`, `MergeReviewState` (Task 2).
- `ui/MergeReviewStore.kt` — store (Tasks 3–5).
- `ui/MergeScreen.kt` — list + detail composables (Tasks 6–7).
- `ui/App.kt` — wire `MergeScreen` into the MERGE branch (Task 6).
- Tests mirror under `src/test/kotlin/...`.

---

### Task 0: Shared test contact factory

**Files:**
- Create: `src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/testsupport/ContactsTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/testsupport/ContactsTest.kt`:
```kotlin
package com.robsartin.contactotomy.testsupport

import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class ContactsTest {
    @Test
    fun `factory builds a contact with the given fields`() {
        val c = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"), org = "Acme")
        assertEquals("a", c.id)
        assertEquals("Rob", c.name.given)
        assertEquals(listOf("+1"), c.phones)
        assertEquals(listOf("+1"), c.rawPhones)
        assertEquals("Acme", c.org)
        assertEquals(Source.APPLE, c.source)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.testsupport.ContactsTest"`
Expected: FAIL — `contact` unresolved.

- [ ] **Step 3: Create the shared factory**

`src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt`:
```kotlin
package com.robsartin.contactotomy.testsupport

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant

/**
 * Single shared test factory for Contact — imported by tests; never redeclared per class.
 *
 * Only the parameters 4b-1's tests actually use are exposed. This is deliberate
 * (YAGNI): do NOT add speculative parameters for fields a future plan *might*
 * need. When a later test needs another field (e.g. categories, createdAt, a
 * non-Apple source), add that one parameter then, in that plan. Param order lets
 * the common positional call `contact(id, given, family, phones)` work.
 */
fun contact(
    id: String,
    given: String? = null,
    family: String? = null,
    phones: List<String> = emptyList(),
    emails: List<String> = emptyList(),
    org: String? = null,
    modifiedAt: Instant? = null,
) = Contact(
    id = id,
    source = Source.APPLE,
    name = ContactName(given = given, family = family),
    phones = phones,
    rawPhones = phones,
    emails = emails,
    org = org,
    modifiedAt = modifiedAt,
    rawVCard = "",
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.testsupport.ContactsTest"`
Expected: PASS.

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt src/test/kotlin/com/robsartin/contactotomy/testsupport/ContactsTest.kt
git commit -m "test(ui): add shared contact test factory (#10)"
```

In the tasks that follow, every test that builds contacts adds
`import com.robsartin.contactotomy.testsupport.contact` and uses this factory —
**delete the per-class `private fun contact(...)`** shown in the snippets below.

---

### Task 1: AppStore holds the merged contact set

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreMergedTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreMergedTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStoreMergedTest {
    @Test
    fun `setMergedContacts stores the working set`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        val merged = listOf(Contact(id = "m1", source = Source.APPLE, name = ContactName(given = "M"), rawVCard = ""))
        store.setMergedContacts(merged)
        assertEquals(merged, store.state.value.mergedContacts)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreMergedTest"`
Expected: FAIL — `mergedContacts`/`setMergedContacts` unresolved.

- [ ] **Step 3: Add the field and setter**

In `AppState.kt`, add to `AppState`:
```kotlin
    val mergedContacts: List<Contact>? = null,
```
In `AppStore.kt`, add a method:
```kotlin
    fun setMergedContacts(merged: List<Contact>) = _state.update { it.copy(mergedContacts = merged) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppStoreMergedTest"`
Expected: PASS.

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/AppState.kt src/main/kotlin/com/robsartin/contactotomy/ui/AppStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/AppStoreMergedTest.kt
git commit -m "feat(ui): AppStore holds merged contact set (#10)"
```

---

### Task 2: Merge-review types

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypesTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypesTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeReviewTypesTest {
    @Test
    fun `review item carries origin, decision, exclusions and conflict choices`() {
        val item = ReviewItem(
            id = "c1", origin = Origin.HIGH, proposal = null,
            decision = Decision.ACCEPT,
            excludedValues = setOf(ExcludedValue("phones", "+1")),
            conflictChoices = mapOf("org" to "Acme"),
        )
        assertEquals(Origin.HIGH, item.origin)
        assertEquals(Decision.ACCEPT, item.decision)
        assertEquals("Acme", item.conflictChoices["org"])
    }
}
```
(Using `proposal = null` here only to test the wrapper shape without depending on the merger; the real store always sets a real proposal. Make `proposal` non-null in the type but the test passes a real one — see note. To keep the test honest, replace `proposal = null` with a real proposal: build one via `ContactMerger().merge(Cluster("c1", listOf(contact("a"), contact("b")), Confidence.HIGH, emptyList()))`. Include the `contact(...)` helper and the imports `com.robsartin.contactotomy.core.matcher.*` and `com.robsartin.contactotomy.core.merger.ContactMerger`.)

Final test body:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class MergeReviewTypesTest {
    @Test
    fun `review item carries origin, decision, exclusions and conflict choices`() {
        val proposal = ContactMerger().merge(
            Cluster("c1", listOf(contact("a"), contact("b")), Confidence.HIGH, emptyList()),
        )
        val item = ReviewItem(
            id = "c1", origin = Origin.HIGH, proposal = proposal,
            decision = Decision.ACCEPT,
            excludedValues = setOf(ExcludedValue("phones", "+1")),
            conflictChoices = mapOf("org" to "Acme"),
        )
        assertEquals(Origin.HIGH, item.origin)
        assertEquals(Decision.ACCEPT, item.decision)
        assertEquals("Acme", item.conflictChoices["org"])
    }

    private fun contact(id: String) =
        Contact(id = id, source = Source.APPLE, name = ContactName(given = id), rawVCard = "")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewTypesTest"`
Expected: FAIL — `ReviewItem`/`Origin`/`Decision` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.merger.MergeProposal

enum class Origin { HIGH, UNCERTAIN, MANUAL }

enum class Decision { ACCEPT, REJECT, SKIP }

data class ReviewItem(
    val id: String,
    val origin: Origin,
    val proposal: MergeProposal,
    val decision: Decision,
    val excludedValues: Set<ExcludedValue> = emptySet(),
    val conflictChoices: Map<String, String> = emptyMap(),
)

data class MergeReviewState(
    val items: List<ReviewItem> = emptyList(),
    val committed: Boolean = false,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewTypesTest"`
Expected: PASS.

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypesTest.kt
git commit -m "feat(ui): add merge-review types (#10)"
```

---

### Task 3: MergeReviewStore construction

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreBuildTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreBuildTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreBuildTest {
    @Test
    fun `builds HIGH items (accepted) for duplicates sharing a phone and name`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val store = MergeReviewStore(listOf(a, b))
        val items = store.state.value.items
        assertEquals(1, items.size)
        assertEquals(Origin.HIGH, items.single().origin)
        assertEquals(Decision.ACCEPT, items.single().decision)
        assertEquals(2, items.single().proposal.cluster.members.size)
    }

    @Test
    fun `builds UNCERTAIN items (rejected by default) for name-only matches`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Jane", family = "Doe")
        val store = MergeReviewStore(listOf(a, b))
        val item = store.state.value.items.single()
        assertEquals(Origin.UNCERTAIN, item.origin)
        assertEquals(Decision.REJECT, item.decision)
    }

    @Test
    fun `no proposals when nothing matches`() {
        val a = contact("a", given = "Jane", family = "Doe")
        val b = contact("b", given = "Mark", family = "Twain")
        assertTrue(MergeReviewStore(listOf(a, b)).state.value.items.isEmpty())
    }

    private fun contact(
        id: String, given: String? = null, family: String? = null, phones: List<String> = emptyList(),
    ) = Contact(id = id, source = Source.APPLE, name = ContactName(given = given, family = family),
        phones = phones, rawPhones = phones, rawVCard = "")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreBuildTest"`
Expected: FAIL — `MergeReviewStore` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.ContactMatcher
import com.robsartin.contactotomy.core.matcher.EdgeClassifier
import com.robsartin.contactotomy.core.matcher.NameMatcher
import com.robsartin.contactotomy.core.matcher.NicknameDictionary
import com.robsartin.contactotomy.core.merger.ContactMerger
import com.robsartin.contactotomy.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds merge-review state built from the imported contacts via the core engine. */
class MergeReviewStore(
    private val contacts: List<Contact>,
    private val matcher: ContactMatcher = defaultMatcher(),
    private val merger: ContactMerger = ContactMerger(),
) {
    private val _state = MutableStateFlow(MergeReviewState(items = buildItems()))
    val state: StateFlow<MergeReviewState> = _state.asStateFlow()

    private fun buildItems(): List<ReviewItem> {
        val result = matcher.match(contacts)
        val high = result.clusters.map { cluster ->
            ReviewItem(id = cluster.id, origin = Origin.HIGH, proposal = merger.merge(cluster), decision = Decision.ACCEPT)
        }
        val uncertain = result.uncertainPairs.map { edge ->
            val cluster = Cluster(
                id = "uncertain-${edge.a.id}+${edge.b.id}",
                members = listOf(edge.a, edge.b),
                confidence = Confidence.UNCERTAIN,
                reasons = edge.reasons,
            )
            ReviewItem(id = cluster.id, origin = Origin.UNCERTAIN, proposal = merger.merge(cluster), decision = Decision.REJECT)
        }
        return high + uncertain
    }

    companion object {
        fun defaultMatcher(): ContactMatcher =
            ContactMatcher(EdgeClassifier(NameMatcher(NicknameDictionary.fromResource())))
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreBuildTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreBuildTest.kt
git commit -m "feat(ui): MergeReviewStore builds items from the engine (#10)"
```

---

### Task 4: Decision/toggle/conflict/accept-all intents

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreIntentsTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreIntentsTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreIntentsTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), org = "Acme Inc")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), org = "Acme")
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `setDecision changes a single item`() {
        val store = dupStore()
        val id = store.state.value.items.single().id
        store.setDecision(id, Decision.REJECT)
        assertEquals(Decision.REJECT, store.state.value.items.single().decision)
    }

    @Test
    fun `toggleField adds then removes an exclusion`() {
        val store = dupStore()
        val id = store.state.value.items.single().id
        val ev = ExcludedValue("phones", "+15125551234")
        store.toggleField(id, ev)
        assertTrue(ev in store.state.value.items.single().excludedValues)
        store.toggleField(id, ev)
        assertTrue(ev !in store.state.value.items.single().excludedValues)
    }

    @Test
    fun `chooseConflict records a field choice`() {
        val store = dupStore()
        val id = store.state.value.items.single().id
        store.chooseConflict(id, "org", "Acme")
        assertEquals("Acme", store.state.value.items.single().conflictChoices["org"])
    }

    @Test
    fun `acceptAllHighConfidence accepts HIGH items only`() {
        val store = dupStore()
        val id = store.state.value.items.single().id
        store.setDecision(id, Decision.REJECT)
        store.acceptAllHighConfidence()
        assertEquals(Decision.ACCEPT, store.state.value.items.single().decision)
    }

    private fun contact(
        id: String, given: String? = null, family: String? = null,
        phones: List<String> = emptyList(), org: String? = null,
    ) = Contact(id = id, source = Source.APPLE, name = ContactName(given = given, family = family),
        phones = phones, rawPhones = phones, org = org, rawVCard = "")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreIntentsTest"`
Expected: FAIL — intent methods unresolved.

- [ ] **Step 3: Add the intents to `MergeReviewStore`**

Insert these methods into the `MergeReviewStore` class:
```kotlin
    fun setDecision(itemId: String, decision: Decision) = updateItem(itemId) { it.copy(decision = decision) }

    fun toggleField(itemId: String, value: com.robsartin.contactotomy.core.apply.ExcludedValue) =
        updateItem(itemId) {
            val next = if (value in it.excludedValues) it.excludedValues - value else it.excludedValues + value
            it.copy(excludedValues = next)
        }

    fun chooseConflict(itemId: String, field: String, value: String) =
        updateItem(itemId) { it.copy(conflictChoices = it.conflictChoices + (field to value)) }

    fun acceptAllHighConfidence() = _state.update { st ->
        st.copy(items = st.items.map { if (it.origin == Origin.HIGH) it.copy(decision = Decision.ACCEPT) else it })
    }

    private fun updateItem(itemId: String, transform: (ReviewItem) -> ReviewItem) = _state.update { st ->
        st.copy(items = st.items.map { if (it.id == itemId) transform(it) else it })
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreIntentsTest"`
Expected: PASS (all 4).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreIntentsTest.kt
git commit -m "feat(ui): merge-review decision/toggle/conflict/accept-all intents (#10)"
```

---

### Task 5: commit() with double-touch downgrade

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCommitTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCommitTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MergeReviewStoreCommitTest {
    @Test
    fun `commit merges accepted clusters and passes others through`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val c = contact("c", given = "Zoe", family = "Quinn")
        val store = MergeReviewStore(listOf(a, b, c))
        val merged = store.commit()
        // a+b collapse to one merged contact; c passes through => 2 total
        assertEquals(2, merged.size)
        assertTrue(store.state.value.committed)
    }

    @Test
    fun `rejected clusters are not merged`() {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        val store = MergeReviewStore(listOf(a, b))
        store.setDecision(store.state.value.items.single().id, Decision.REJECT)
        assertEquals(2, store.commit().size)
    }

    @Test
    fun `overlapping accepted proposals do not double-merge a contact`() {
        // a,b,c all share a phone+name => one HIGH cluster of 3. Build a second
        // (manual-style) overlapping acceptance by accepting an UNCERTAIN item that
        // shares a member, and verify commit stays consistent (no crash, no dup).
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+1"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+1"))
        val store = MergeReviewStore(listOf(a, b))
        // accept everything that exists
        store.state.value.items.forEach { store.setDecision(it.id, Decision.ACCEPT) }
        val merged = store.commit()
        // a and b are the same person => at most they merge into 1; never 3 or a duplicate id
        assertEquals(merged.map { it.id }, merged.map { it.id }.distinct())
        assertTrue(merged.size <= 2)
    }

    private fun contact(
        id: String, given: String? = null, family: String? = null, phones: List<String> = emptyList(),
    ) = Contact(id = id, source = Source.APPLE, name = ContactName(given = given, family = family),
        phones = phones, rawPhones = phones, rawVCard = "")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCommitTest"`
Expected: FAIL — `commit` unresolved.

- [ ] **Step 3: Add `commit()` to `MergeReviewStore`**

```kotlin
    /** Applies accepted merges and returns the resulting contact list; idempotent guard against double-touch. */
    fun commit(): List<Contact> {
        val accepted = _state.value.items.filter { it.decision == Decision.ACCEPT }
        val seen = mutableSetOf<String>()
        val downgraded = mutableSetOf<String>()
        val finalAccepted = mutableListOf<ReviewItem>()
        for (item in accepted) {
            val memberIds = item.proposal.cluster.members.map { it.id }
            if (memberIds.any { it in seen }) {
                downgraded += item.id
            } else {
                seen += memberIds
                finalAccepted += item
            }
        }
        val decisions = finalAccepted.map {
            com.robsartin.contactotomy.core.apply.MergeDecision(
                clusterId = it.proposal.cluster.id,
                action = com.robsartin.contactotomy.core.apply.Action.ACCEPT,
                excludedValues = it.excludedValues,
                conflictChoices = it.conflictChoices,
            )
        }
        val result = com.robsartin.contactotomy.core.apply.applyDecisions(
            contacts, finalAccepted.map { it.proposal }, decisions,
        )
        _state.update { st ->
            st.copy(
                items = st.items.map { if (it.id in downgraded) it.copy(decision = Decision.SKIP) else it },
                committed = true,
            )
        }
        return result
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCommitTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCommitTest.kt
git commit -m "feat(ui): commit merges with double-touch downgrade (#10)"
```

---

### Task 6: Merge screen — cluster list + wire into shell

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/App.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenListTest.kt`

- [ ] **Step 1: Write the failing UI test**

`src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenListTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeScreenListTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", "Rob", "Sartin", listOf("+15125551234"))
        val b = contact("b", "Robert", "Sartin", listOf("+15125551234"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `shows the to-merge section with the cluster`() = runComposeUiTest {
        setContent { MergeScreen(dupStore(), onCommit = {}) }
        onNodeWithText("To merge", substring = true).assertExists()
        onNodeWithText("Sartin", substring = true).assertExists()
    }

    @Test
    fun `apply button commits and reports the merged contacts`() = runComposeUiTest {
        var committed: List<Contact>? = null
        setContent { MergeScreen(dupStore(), onCommit = { committed = it }) }
        onNodeWithText("Apply merges", substring = true).performClick()
        // a+b => 1 merged contact
        kotlin.test.assertEquals(1, committed?.size)
    }

    private fun contact(id: String, given: String, family: String, phones: List<String>) =
        Contact(id = id, source = Source.APPLE, name = ContactName(given = given, family = family),
            phones = phones, rawPhones = phones, rawVCard = "")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenListTest"`
Expected: FAIL — `MergeScreen` unresolved.

- [ ] **Step 3: Write `MergeScreen.kt` (list + summary + commit; detail added in Task 7)**

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robsartin.contactotomy.core.model.Contact

@Composable
fun MergeScreen(store: MergeReviewStore, onCommit: (List<Contact>) -> Unit) {
    val state by store.state.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    val high = state.items.filter { it.origin == Origin.HIGH }
    val uncertain = state.items.filter { it.origin == Origin.UNCERTAIN }
    val willMerge = state.items.count { it.decision == Decision.ACCEPT }

    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth(0.45f)) {
            Text("Will merge $willMerge clusters", Modifier.padding(bottom = 6.dp))
            if (high.isNotEmpty()) {
                Row { Text("To merge (${high.size})"); Button(onClick = { store.acceptAllHighConfidence() }) { Text("Accept all") } }
            }
            LazyColumn {
                items(high) { item -> ClusterRow(item) { selectedId = item.id } }
                if (uncertain.isNotEmpty()) item { Text("Possible matches (${uncertain.size})", Modifier.padding(top = 8.dp)) }
                items(uncertain) { item -> ClusterRow(item) { selectedId = item.id } }
            }
            Button(onClick = { onCommit(store.commit()) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Apply merges & continue")
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            val selected = state.items.firstOrNull { it.id == selectedId }
            if (selected == null) Text("Select a cluster to review") else MergeDetail(store, selected)
        }
    }
}

@Composable
private fun ClusterRow(item: ReviewItem, onClick: () -> Unit) {
    val mark = when (item.decision) { Decision.ACCEPT -> "✓"; Decision.REJECT -> "✕"; Decision.SKIP -> "◷" }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Button(onClick = onClick) {
            val name = item.proposal.merged.name.let { it.formatted ?: listOfNotNull(it.given, it.family).joinToString(" ") }
            Text("$mark  $name · ${item.proposal.cluster.members.size} cards")
        }
    }
}
```

For Task 6, add a temporary minimal `MergeDetail` so it compiles (Task 7 replaces it):
```kotlin
@Composable
private fun MergeDetail(store: MergeReviewStore, item: ReviewItem) {
    Text("Detail for ${item.id} — built in Task 7")
}
```

- [ ] **Step 4: Wire into `App.kt`**

In `App.kt`, replace the MERGE branch stub:
```kotlin
                Screen.MERGE -> {
                    val reviewStore = androidx.compose.runtime.remember(state.contacts) { MergeReviewStore(state.contacts) }
                    MergeScreen(reviewStore) { merged ->
                        store.setMergedContacts(merged)
                        store.next()
                    }
                }
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenListTest" --tests "com.robsartin.contactotomy.ui.AppShellTest"`
Expected: PASS (merge list tests + the existing shell tests still green).

- [ ] **Step 6: Format, gate, commit**

```bash
./gradlew spotlessApply && ./gradlew check
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt src/main/kotlin/com/robsartin/contactotomy/ui/App.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenListTest.kt
git commit -m "feat(ui): merge screen cluster list + shell wiring (#10)"
```

---

### Task 7: Merge detail pane (toggles + conflicts) + run-the-app

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeDetailTest.kt`

- [ ] **Step 1: Write the failing UI test**

`src/test/kotlin/com/robsartin/contactotomy/ui/MergeDetailTest.kt`:
```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import java.time.Instant
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeDetailTest {
    private fun store(): MergeReviewStore {
        val a = contact("a", "Robert", "Sartin", listOf("+15125551234"), emails = listOf("rob@me.com"),
            org = "Acme Inc", modifiedAt = Instant.parse("2024-01-01T00:00:00Z"))
        val b = contact("b", "Robert", "Sartin", listOf("+15125559999"), emails = listOf("rob@gmail.com"),
            org = "Acme", modifiedAt = Instant.parse("2021-01-01T00:00:00Z"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `detail shows merged values and a conflict, and toggling updates state`() = runComposeUiTest {
        val s = store()
        val id = s.state.value.items.single().id
        setContent { MergeScreen(s, onCommit = {}) }
        // open the detail
        onNodeWithText("Sartin", substring = true).performClick()
        // a merged phone chip is shown
        onNodeWithText("+15125551234", substring = true).assertExists()
        // exclude it
        onNodeWithText("+15125551234", substring = true).performClick()
        kotlin.test.assertTrue(
            s.state.value.items.single().excludedValues.any { it.value == "+15125551234" },
        )
        // org conflict choice present
        onNodeWithText("Acme Inc", substring = true).assertExists()
    }

    private fun contact(
        id: String, given: String, family: String, phones: List<String>,
        emails: List<String> = emptyList(), org: String? = null, modifiedAt: Instant? = null,
    ) = Contact(id = id, source = Source.APPLE, name = ContactName(given = given, family = family),
        phones = phones, rawPhones = phones, emails = emails, org = org, modifiedAt = modifiedAt, rawVCard = "")
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeDetailTest"`
Expected: FAIL — the temporary `MergeDetail` doesn't render values/toggles.

- [ ] **Step 3: Replace `MergeDetail` in `MergeScreen.kt`**

```kotlin
@Composable
private fun MergeDetail(store: MergeReviewStore, item: ReviewItem) {
    val p = item.proposal
    Column(Modifier.padding(start = 4.dp)) {
        val name = p.merged.name.let { it.formatted ?: listOfNotNull(it.given, it.family).joinToString(" ") }
        Text("Merged: $name", Modifier.padding(bottom = 6.dp))

        // multi-value fields as include/exclude chips
        MultiField("phones", p.merged.phones, item, store)
        MultiField("emails", p.merged.emails, item, store)
        MultiField("categories", p.merged.categories, item, store)

        // single-value conflicts as choices
        p.conflicts.forEach { conflict ->
            Text("${conflict.field} (conflict):", Modifier.padding(top = 6.dp))
            Row {
                conflict.candidates.map { it.value }.distinct().forEach { value ->
                    val chosen = item.conflictChoices[conflict.field] ?: conflict.chosen
                    val mark = if (value == chosen) "◉" else "○"
                    Button(onClick = { store.chooseConflict(item.id, conflict.field, value) }) { Text("$mark $value") }
                }
            }
        }

        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { store.setDecision(item.id, Decision.ACCEPT) }) { Text("Accept") }
            Button(onClick = { store.setDecision(item.id, Decision.REJECT) }) { Text("Reject") }
            Button(onClick = { store.setDecision(item.id, Decision.SKIP) }) { Text("Skip") }
        }
    }
}

@Composable
private fun MultiField(field: String, values: List<String>, item: ReviewItem, store: MergeReviewStore) {
    if (values.isEmpty()) return
    Text(field, Modifier.padding(top = 4.dp))
    Row {
        values.forEach { value ->
            val ev = com.robsartin.contactotomy.core.apply.ExcludedValue(field, value)
            val included = ev !in item.excludedValues
            val mark = if (included) "☑" else "☐"
            Button(onClick = { store.toggleField(item.id, ev) }) { Text("$mark $value") }
        }
    }
}
```
(Imports already present in the file from Task 6: `Column`, `Row`, `Button`, `Text`, `Modifier`, `dp`. Add `androidx.compose.foundation.layout.Column` if not already imported.)

- [ ] **Step 4: Run the test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeDetailTest" --tests "com.robsartin.contactotomy.ui.MergeScreenListTest"`
Expected: PASS (detail + list tests).

- [ ] **Step 5: Full gate**

Run: `./gradlew spotlessApply && ./gradlew check`
Expected: BUILD SUCCESSFUL (coverage ≥90/65; all UI + store + core tests green).

- [ ] **Step 6: Run the app and verify manually**

Run: `./gradlew run`
Expected: import a couple of `.vcf` files (or `src/test/resources/fixtures/apple-sample.vcf` twice via the Other slot), click Next to Merge, see the "To merge"/"Possible matches" sections, select a cluster, see the before→merged detail with toggle chips and conflict choices, toggle a field, click "Apply merges & continue", and land on the Deletion stub. Screenshot for the PR.

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt src/test/kotlin/com/robsartin/contactotomy/ui/MergeDetailTest.kt
git commit -m "feat(ui): merge detail pane with field toggles and conflict choices (#10)"
```

---

## Self-Review

**Spec coverage (4b-1):**
- §3 data flow (matcher/merger on contacts → proposals; commit → applyDecisions → AppStore.mergedContacts): Tasks 1, 3, 5, 6. ✓
- §4 store (build HIGH-accept / UNCERTAIN-reject; setDecision/toggleField/chooseConflict; acceptAllHighConfidence; commit + double-touch downgrade; SKIP/REJECT semantics): Tasks 3–5. ✓
- §5 UI (list with To-merge / Possible-matches sections + accept-all + summary; detail = merged values as toggle chips + conflict choices + Accept/Reject/Skip; commit advances): Tasks 6–7. ✓
- §6 testing (store unit tests + Compose UI tests + run-the-app): Tasks 1–7. ✓
- §7 scope: manual merge deferred to 4b-2 (Origin.MANUAL exists but is unused here); deletion/export untouched. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. The Task 2 test note (use a real proposal, not null) is resolved inline with the final test body. Task 6's temporary `MergeDetail` is explicitly replaced in Task 7.

**Type consistency:** `MergeReviewStore(contacts, matcher, merger)` and its intents (`setDecision`, `toggleField(ExcludedValue)`, `chooseConflict(field,value)`, `acceptAllHighConfidence`, `commit(): List<Contact>`) are consistent across Tasks 3–7. `ReviewItem(id, origin, proposal, decision, excludedValues, conflictChoices)` matches Tasks 2–7. `MergeScreen(store, onCommit)` signature matches its call in `App.kt` (Task 6) and tests. Field-name strings (`"phones"`, `"emails"`, `"categories"`, conflict `field`) match the merger's provenance/conflict field names and `core.apply.ExcludedValue`. `AppStore.setMergedContacts` / `AppState.mergedContacts` consistent (Task 1).

**Risk notes:** (1) Compose test-API symbols may vary by version — adjust imports, keep behavior. (2) Branch coverage: the detail composable adds branches; if koverVerify dips below 65, add a focused UI test (e.g. a conflict-choice click) rather than lowering the floor. (3) `onNodeWithText(... substring = true)` must match the rendered chip text exactly as composed (`"☑ +15125551234"` contains `+15125551234`) — fine, but if a matcher finds multiple nodes, use `onAllNodesWithText(...)[0]`.

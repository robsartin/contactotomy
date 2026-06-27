# Name + Company (org) Choice Implementation Plan (#29 Phase 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In the merge detail, let the user choose the person name AND the company/org — including auto-promoting a mis-filed company name (in the name field) into the merged org — driven by a pure company-name detector.

**Architecture:** A pure `core` `CompanyNameDetector` classifies a name as company-like. The merge review store gains an `orgChoice` override (mirroring the existing name override) applied in `commit()`, plus auto-suggest defaults computed when building items. The merge screen badges company-like names and adds a dedicated "Company / org" control. The matcher/merger/apply engine and the exporter are untouched — the exporter already serializes structured `Contact.org`/`name`.

**Tech Stack:** Kotlin, Compose Desktop (Material), `runComposeUiTest`, kotlinx-coroutines StateFlow, kotlin-test, Kover (line ≥90 / branch ≥70), Spotless/ktlint, Konsist.

Branch: `29-name-and-company` (off `main`). Issue: #29.
Spec: `docs/superpowers/specs/2026-06-27-name-and-company-choice-design.md`.

Run tests with plain `./gradlew test` on macOS (NO xvfb; that is CI-only). Compose test compilation is slow (minutes) — normal.

## File Structure

- Create `src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt` — pure detector (`CompanySignal` enum + `CompanyNameDetector` object).
- Create `src/main/kotlin/com/robsartin/contactotomy/ui/ContactDisplay.kt` — shared `displayName(ContactName)` (extracted from `MergeScreen.kt`).
- Modify `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt` — remove the private `displayName`; name badge; "Company / org" control; skip `org` in the generic conflict loop.
- Modify `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt` — add `orgChoice`.
- Modify `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt` — `chooseOrg`, auto-suggest helper, shared `reviewItem(...)` builder, org override in `commit()`.
- Tests: `core/company/CompanyNameDetectorTest.kt`, `ui/MergeReviewStoreCompanyTest.kt`, `ui/MergeScreenCompanyTest.kt`, and a case + fixture in `ui/AppFlowTest.kt` / `resources/fixtures/name-company.vcf`.

---

### Task 1: Extract shared `displayName` to `ui/ContactDisplay.kt`

Pure refactor so both the screen and the store can use one `displayName`. Behavior unchanged.

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/ContactDisplay.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`

- [ ] **Step 1: Create the shared helper**

Create `src/main/kotlin/com/robsartin/contactotomy/ui/ContactDisplay.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.ContactName

/** Human-readable name for a contact: the formatted name, else given + family. */
fun displayName(name: ContactName): String =
    name.formatted?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(name.given, name.family).joinToString(" ")
```

- [ ] **Step 2: Remove the private copy in `MergeScreen.kt`**

Delete this line near the bottom of `MergeScreen.kt`:

```kotlin
private fun displayName(name: ContactName): String = name.formatted ?: listOfNotNull(name.given, name.family).joinToString(" ")
```

Leave every `displayName(...)` call site as-is — they now resolve to the shared top-level function in the same package. If the `import com.robsartin.contactotomy.core.model.ContactName` in `MergeScreen.kt` becomes unused after this deletion, remove it (ktlint will flag an unused import).

- [ ] **Step 3: Run the build to verify it compiles and tests pass**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (this is a behavior-preserving refactor; existing MergeScreen tests still pass).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/ContactDisplay.kt src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt
git commit -m "refactor(ui): extract shared displayName helper (#29)"
```

---

### Task 2: `CompanyNameDetector` (pure core util)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetectorTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CompanyNameDetectorTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.ContactName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompanyNameDetectorTest {
    private fun named(
        formatted: String? = null,
        given: String? = null,
        family: String? = null,
    ) = ContactName(given = given, family = family, formatted = formatted)

    @Test
    fun `legal suffix is detected`() {
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "Acme Inc")))
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "Acme Inc.")))
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(given = "Acme", family = "LLC")))
    }

    @Test
    fun `ampersand is detected`() {
        assertEquals(CompanySignal.AMPERSAND, CompanyNameDetector.detect(named(formatted = "Smith & Sons")))
        assertEquals(CompanySignal.AMPERSAND, CompanyNameDetector.detect(named(formatted = "Jones and Co")))
    }

    @Test
    fun `keyword is detected`() {
        assertEquals(CompanySignal.KEYWORD, CompanyNameDetector.detect(named(formatted = "Joe's Plumbing")))
        assertEquals(CompanySignal.KEYWORD, CompanyNameDetector.detect(named(formatted = "Bright Solutions")))
    }

    @Test
    fun `weak signals - all caps or single token with no family`() {
        assertEquals(CompanySignal.WEAK, CompanyNameDetector.detect(named(formatted = "ACME")))
        assertEquals(CompanySignal.WEAK, CompanyNameDetector.detect(given = "Dave"))
    }

    @Test
    fun `ordinary person names are not company-like`() {
        assertNull(CompanyNameDetector.detect(named(given = "Jane", family = "Smith")))
        assertNull(CompanyNameDetector.detect(named(given = "Jane", family = "Baker")))
        assertNull(CompanyNameDetector.detect(named(formatted = "")))
    }

    @Test
    fun `highest-precision signal wins`() {
        // all-caps (WEAK) + keyword + legal suffix -> LEGAL_SUFFIX
        assertEquals(CompanySignal.LEGAL_SUFFIX, CompanyNameDetector.detect(named(formatted = "ACME PLUMBING INC")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNameDetectorTest"`
Expected: FAIL — `CompanyNameDetector` / `CompanySignal` unresolved.

- [ ] **Step 3: Write the implementation**

Create `CompanyNameDetector.kt`:

```kotlin
package com.robsartin.contactotomy.core.company

import com.robsartin.contactotomy.core.model.ContactName

/** Which signal flagged a name as company-like (precision order: LEGAL_SUFFIX highest). */
enum class CompanySignal { LEGAL_SUFFIX, AMPERSAND, KEYWORD, WEAK }

/** Pure heuristic: does a name field actually hold a company name? Returns the strongest signal, or null. */
object CompanyNameDetector {
    private val SUFFIXES =
        setOf(
            "INC", "LLC", "LTD", "CORP", "CO", "GMBH", "PLC", "SA", "LLP", "LP",
            "GROUP", "HOLDINGS", "CORPORATION", "INCORPORATED", "COMPANY",
        )
    private val KEYWORDS =
        setOf(
            "SERVICES", "SOLUTIONS", "RESTAURANT", "PLUMBING", "SALON", "CLINIC",
            "STUDIO", "BANK", "AGENCY", "CONSULTING", "SYSTEMS", "TECHNOLOGIES",
            "ENTERPRISES", "INDUSTRIES",
        )
    private val AND_CO = Regex("(?i)\\b(and|&)\\s+co\\b")

    fun detect(name: ContactName): CompanySignal? {
        val display =
            name.formatted?.takeIf { it.isNotBlank() }
                ?: listOfNotNull(name.given, name.family).joinToString(" ")
        if (display.isBlank()) return null
        val tokens = display.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cleaned = tokens.map { it.replace(".", "").replace(",", "").uppercase() }

        if (cleaned.isNotEmpty() && cleaned.last() in SUFFIXES) return CompanySignal.LEGAL_SUFFIX
        if (display.contains("&") || AND_CO.containsMatchIn(display)) return CompanySignal.AMPERSAND
        if (cleaned.any { it in KEYWORDS }) return CompanySignal.KEYWORD
        val allCaps = display.any { it.isLetter() } && display == display.uppercase()
        val singleToken = tokens.size == 1 && name.family.isNullOrBlank()
        if (allCaps || singleToken) return CompanySignal.WEAK
        return null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNameDetectorTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetectorTest.kt
git commit -m "feat(core): CompanyNameDetector classifies company-like names (#29)"
```

---

### Task 3: `orgChoice` field + `chooseOrg` intent

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `MergeReviewStoreCompanyTest.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MergeReviewStoreCompanyTest {
    // a & b cluster on shared phone + identical name; both have an org so no auto-suggest.
    private fun orgCluster(): MergeReviewStore {
        val a = contact("a", given = "Pat", family = "Lee", org = "Acme", phones = listOf("+15125559999"))
        val b = contact("b", given = "Pat", family = "Lee", org = "Acme", phones = listOf("+15125559999"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `chooseOrg sets orgChoice`() {
        val store = orgCluster()
        val id = store.state.value.items.single().id
        store.chooseOrg(id, "Foo Corp")
        assertEquals("Foo Corp", store.state.value.items.single().orgChoice)
    }

    @Test
    fun `orgChoice defaults to null`() {
        assertNull(orgCluster().state.value.items.single().orgChoice)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"`
Expected: FAIL — `orgChoice` / `chooseOrg` unresolved.

- [ ] **Step 3: Add the field and the intent**

In `MergeReviewTypes.kt`, add to `ReviewItem` (after `nameChoiceId`):

```kotlin
    val orgChoice: String? = null,
```

In `MergeReviewStore.kt`, add this intent after `chooseName(...)`:

```kotlin
    fun chooseOrg(
        itemId: String,
        value: String,
    ) = updateItem(itemId) { it.copy(orgChoice = value) }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt \
        src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt
git commit -m "feat(ui): orgChoice override field + chooseOrg intent (#29)"
```

---

### Task 4: `commit()` applies the org override

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `MergeReviewStoreCompanyTest.kt`:

```kotlin
    @Test
    fun `commit applies a chosen org to the merged contact`() {
        val store = orgCluster()
        val id = store.state.value.items.single().id
        store.chooseOrg(id, "Acme Incorporated")
        store.accept(id)
        val result = store.commit()
        assertEquals(1, result.size)
        assertEquals("Acme Incorporated", result.single().org)
    }

    @Test
    fun `commit with an empty org choice clears the merged org`() {
        val store = orgCluster() // merged would otherwise be "Acme"
        val id = store.state.value.items.single().id
        store.chooseOrg(id, "")
        store.accept(id)
        val result = store.commit()
        assertNull(result.single().org)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"`
Expected: FAIL — `org` is still "Acme" (override not applied yet).

- [ ] **Step 3: Apply the override in `commit()`**

In `MergeReviewStore.kt`, in `commit()`, right after the `withNames` line:

```kotlin
        val withNames = result.map { c -> nameOverrides[c.id]?.let { c.copy(name = it) } ?: c }
```

add:

```kotlin
        // Apply per-cluster org overrides (chosen company/org, or "" to clear) — engine untouched.
        val orgOverrides: Map<String, String> =
            finalAccepted.mapNotNull { item -> item.orgChoice?.let { item.proposal.merged.id to it } }.toMap()
        val withOrg =
            withNames.map { c -> orgOverrides[c.id]?.let { oc -> c.copy(org = oc.ifEmpty { null }) } ?: c }
```

and change the final `return withNames` to:

```kotlin
        return withOrg
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt
git commit -m "feat(ui): commit applies the org override to the merged contact (#29)"
```

---

### Task 5: Auto-suggest company promotion in `buildItems` and `manualMerge`

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `MergeReviewStoreCompanyTest.kt`:

```kotlin
    @Test
    fun `manualMerge auto-suggests org from a company name and person name from another card`() {
        val jane = contact("jane", given = "Jane", family = "Smith", emails = listOf("jane@acme.com"))
        val acme = contact("acme", given = "Acme", family = "Inc", emails = listOf("info@acme.com"))
        val store = MergeReviewStore(listOf(jane, acme))
        val id = store.manualMerge(listOf("jane", "acme"))!!
        val item = store.state.value.items.first { it.id == id }
        assertEquals("Acme Inc", item.orgChoice)
        assertEquals("jane", item.nameChoiceId)
    }

    @Test
    fun `auto-suggest does not fire when a source already has an org`() {
        val jane = contact("jane", given = "Jane", family = "Smith")
        val acme = contact("acme", given = "Acme", family = "Inc", org = "Acme Corporation")
        val store = MergeReviewStore(listOf(jane, acme))
        val id = store.manualMerge(listOf("jane", "acme"))!!
        val item = store.state.value.items.first { it.id == id }
        assertNull(item.orgChoice)
        assertNull(item.nameChoiceId)
    }

    @Test
    fun `buildItems auto-suggests org for a clustered company card with no org`() {
        // identical company-like names + shared phone => one HIGH cluster
        val c1 = contact("c1", given = "Bobs", family = "Plumbing", phones = listOf("+15125550000"))
        val c2 = contact("c2", given = "Bobs", family = "Plumbing", phones = listOf("+15125550000"))
        val store = MergeReviewStore(listOf(c1, c2))
        assertEquals("Bobs Plumbing", store.state.value.items.single().orgChoice)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"`
Expected: FAIL — `orgChoice`/`nameChoiceId` are null (auto-suggest not implemented).

Note: if `buildItems auto-suggests...` fails because the two `Bobs Plumbing` cards did NOT form a cluster (the items list is empty or has 2 items), the matcher did not pair them — adjust the fixture minimally so they cluster (e.g. add an identical shared email to both), keeping one company-like name and no org. Do not weaken the assertion.

- [ ] **Step 3: Implement the auto-suggest helper and wire it into both builders**

In `MergeReviewStore.kt`:

(a) Add the import:

```kotlin
import com.robsartin.contactotomy.core.company.CompanyNameDetector
```

(b) Add a shared item builder and the auto-suggest helper (place them as private members, e.g. just above `updateItem`):

```kotlin
    /** Builds a ReviewItem for a cluster, applying company auto-suggest defaults. */
    private fun reviewItem(
        id: String,
        origin: Origin,
        cluster: Cluster,
    ): ReviewItem {
        val (nameChoiceId, orgChoice) = companyAutoSuggest(cluster.members)
        return ReviewItem(
            id = id,
            origin = origin,
            proposal = merger.merge(cluster),
            nameChoiceId = nameChoiceId,
            orgChoice = orgChoice,
        )
    }

    /**
     * When no member has an org but some member's name looks like a company, suggest promoting that
     * name into org, and (if a different member has a non-company name) using that as the person name.
     * Returns (nameChoiceId, orgChoice); both null if nothing to suggest.
     */
    private fun companyAutoSuggest(members: List<Contact>): Pair<String?, String?> {
        if (members.any { !it.org.isNullOrBlank() }) return null to null
        val companyMembers = members.mapNotNull { m -> CompanyNameDetector.detect(m.name)?.let { m to it } }
        if (companyMembers.isEmpty()) return null to null
        val company = companyMembers.minByOrNull { it.second.ordinal }!!.first
        val personId =
            members.firstOrNull { it.id != company.id && CompanyNameDetector.detect(it.name) == null && displayName(it.name).isNotBlank() }?.id
        return personId to displayName(company.name)
    }
```

(c) Rewrite `buildItems()` to use `reviewItem(...)`:

```kotlin
    private fun buildItems(): List<ReviewItem> {
        val result = matcher.match(contacts)
        val high =
            result.clusters.map { cluster ->
                reviewItem(id = cluster.id, origin = Origin.HIGH, cluster = cluster)
            }
        val uncertain =
            result.uncertainPairs.map { edge ->
                val cluster =
                    Cluster(
                        id = "uncertain-${edge.a.id}+${edge.b.id}",
                        members = listOf(edge.a, edge.b),
                        confidence = Confidence.UNCERTAIN,
                        reasons = edge.reasons,
                    )
                reviewItem(id = cluster.id, origin = Origin.UNCERTAIN, cluster = cluster)
            }
        return high + uncertain
    }
```

(d) In `manualMerge(...)`, replace the line that builds `item`:

```kotlin
        val item = ReviewItem(id = cluster.id, origin = Origin.MANUAL, proposal = merger.merge(cluster))
```

with:

```kotlin
        val item = reviewItem(id = cluster.id, origin = Origin.MANUAL, cluster = cluster)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"`
Expected: PASS.

- [ ] **Step 5: Run the whole suite to confirm no regression**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL (existing build/intents/manual-merge store tests still pass; auto-suggest only changes defaults when no source has org + a name is company-like).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt
git commit -m "feat(ui): auto-suggest promoting a company-name into org (#29)"
```

---

### Task 6: Name badge in the detail pane

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenCompanyTest.kt` (create)

- [ ] **Step 1: Write the failing test**

Create `MergeScreenCompanyTest.kt`:

```kotlin
package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeScreenCompanyTest {
    // jane (person) + acme (company mis-filed as name), no orgs => one manual item, auto-selected.
    private fun janeAcmeStore(): MergeReviewStore {
        val jane = contact("jane", given = "Jane", family = "Smith", emails = listOf("jane@acme.com"))
        val acme = contact("acme", given = "Acme", family = "Inc", emails = listOf("info@acme.com"))
        val store = MergeReviewStore(listOf(jane, acme))
        store.manualMerge(listOf("jane", "acme"))
        return store
    }

    @Test
    fun `a company-like name is badged in the detail pane`() =
        runComposeUiTest {
            setContent { MergeScreen(janeAcmeStore()) }
            onNodeWithText("looks like a company", substring = true).assertIsDisplayed()
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenCompanyTest"`
Expected: FAIL — no "looks like a company" node.

- [ ] **Step 3: Add the badge to the name radio**

In `MergeScreen.kt`, add the import:

```kotlin
import com.robsartin.contactotomy.core.company.CompanyNameDetector
```

In `MergeDetailContent`, replace the whole name block:

```kotlin
        // Name: pick which source card's name wins (most-complete pre-selected = merged.name).
        val names =
            p.cluster.members
                .associate { it.id to displayName(it.name) }
                .filterValues { it.isNotBlank() }
        if (names.isNotEmpty()) {
            Text("Name (pick one)")
            names.forEach { (memberId, name) ->
                val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = memberId == chosen, onClick = { store.chooseName(item.id, memberId) })
                    Text(name)
                }
            }
        }
```

with:

```kotlin
        // Name: pick which source card's name wins; company-like names are badged.
        val namedMembers = p.cluster.members.filter { displayName(it.name).isNotBlank() }
        if (namedMembers.isNotEmpty()) {
            Text("Name (pick one)")
            namedMembers.forEach { m ->
                val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                val badge = if (CompanyNameDetector.detect(m.name) != null) "  · looks like a company" else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = m.id == chosen, onClick = { store.chooseName(item.id, m.id) })
                    Text(displayName(m.name) + badge)
                }
            }
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenCompanyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenCompanyTest.kt
git commit -m "feat(ui): badge company-like names in the merge detail (#29)"
```

---

### Task 7: "Company / org" control; drop org from the generic conflict loop

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenCompanyTest.kt`

- [ ] **Step 1: Write the failing tests**

Add to `MergeScreenCompanyTest.kt`:

```kotlin
    @Test
    fun `company-org control promotes a mis-filed name and (none) clears it`() =
        runComposeUiTest {
            val store = janeAcmeStore() // auto-suggest set orgChoice = "Acme Inc"
            setContent { MergeScreen(store) }
            onNodeWithText("Company / org", substring = true).assertIsDisplayed()
            onNodeWithText("Acme Inc (from name)", substring = true).assertIsDisplayed()
            onNodeWithText("(none)").performClick()
            assertEquals("", store.state.value.items.single().orgChoice)
        }

    @Test
    fun `org is not shown in the generic conflict list`() =
        runComposeUiTest {
            // two cards with differing orgs -> previously an "org (pick one)" conflict row
            val a = contact("a", given = "Pat", family = "Lee", org = "Acme", phones = listOf("+15125559999"))
            val b = contact("b", given = "Pat", family = "Lee", org = "Acme Inc", phones = listOf("+15125559999"))
            val store = MergeReviewStore(listOf(a, b))
            val id = store.state.value.items.single().id
            setContent { MergeScreen(store) }
            // choosing the company/org radio records orgChoice (not a conflict choice)
            onNodeWithText("Acme Inc", substring = true).performClick()
            assertEquals("Acme Inc", store.state.value.items.single().orgChoice)
            assertEquals(false, store.state.value.items.single().conflictChoices.containsKey("org"))
            onNodeWithText("org (pick one)", substring = true).assertDoesNotExist()
        }
```

Add these imports to the test file:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenCompanyTest"`
Expected: FAIL — no "Company / org" node; org still appears as a generic "org (pick one)" conflict.

- [ ] **Step 3: Add the control and skip org in conflicts**

In `MergeScreen.kt`, in `MergeDetailContent`, replace the conflict loop:

```kotlin
        // Single-value conflicts (org/title/notes): pick one with a radio.
        p.conflicts.forEach { conflict ->
            Text("${conflict.field} (pick one)", Modifier.padding(top = 4.dp))
            conflict.candidates.map { it.value }.distinct().forEach { value ->
                val chosen = item.conflictChoices[conflict.field] ?: conflict.chosen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = value == chosen,
                        onClick = { store.chooseConflict(item.id, conflict.field, value) },
                    )
                    Text(value)
                }
            }
        }
```

with:

```kotlin
        // Company / org has its own control (supports promoting a mis-filed company name).
        CompanyOrgField(store, item)

        // Single-value conflicts (title/notes): pick one with a radio. (org handled above.)
        p.conflicts.filter { it.field != "org" }.forEach { conflict ->
            Text("${conflict.field} (pick one)", Modifier.padding(top = 4.dp))
            conflict.candidates.map { it.value }.distinct().forEach { value ->
                val chosen = item.conflictChoices[conflict.field] ?: conflict.chosen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = value == chosen,
                        onClick = { store.chooseConflict(item.id, conflict.field, value) },
                    )
                    Text(value)
                }
            }
        }
```

Then add the `CompanyOrgField` composable (place it just after `MergeDetailContent`):

```kotlin
@Composable
private fun CompanyOrgField(
    store: MergeReviewStore,
    item: ReviewItem,
) {
    val members = item.proposal.cluster.members
    val orgs = members.mapNotNull { it.org?.takeIf { o -> o.isNotBlank() } }
    val promotions =
        members.mapNotNull { m ->
            if (CompanyNameDetector.detect(m.name) != null) displayName(m.name).takeIf { it.isNotBlank() } else null
        }
    // value -> label, insertion-ordered, de-duplicated; promotions tagged "(from name)"; "" = none.
    val candidates = LinkedHashMap<String, String>()
    orgs.forEach { candidates.putIfAbsent(it, it) }
    promotions.forEach { candidates.putIfAbsent(it, "$it (from name)") }
    candidates[""] = "(none)"

    val chosen = item.orgChoice ?: orgs.firstOrNull() ?: ""
    Text("Company / org (pick one)", Modifier.padding(top = 4.dp))
    candidates.forEach { (value, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = value == chosen, onClick = { store.chooseOrg(item.id, value) })
            Text(label)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenCompanyTest"`
Expected: PASS (all three tests in the class).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenCompanyTest.kt
git commit -m "feat(ui): dedicated Company/org control with name promotion (#29)"
```

---

### Task 8: End-to-end — Jane Smith + Acme Inc exports with the promoted org

**Files:**
- Create: `src/test/resources/fixtures/name-company.vcf`
- Modify: `src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt`

- [ ] **Step 1: Create the fixture**

Create `src/test/resources/fixtures/name-company.vcf` (a person card + a company-mis-filed-as-name card; distinct names so the matcher leaves them as singletons, paired by manual merge):

```
BEGIN:VCARD
VERSION:3.0
N:Smith;Jane;;;
FN:Jane Smith
EMAIL;TYPE=INTERNET:jane@acme.com
END:VCARD
BEGIN:VCARD
VERSION:3.0
N:;Acme Inc;;;
FN:Acme Inc
EMAIL;TYPE=INTERNET:info@acme.com
END:VCARD
```

- [ ] **Step 2: Write the failing test**

Add to `AppFlowTest.kt`. First ensure the import for the exporter is present (add if missing):

```kotlin
import com.robsartin.contactotomy.core.exporter.VcfExporter
```

Then add the test method to the `AppFlowTest` class:

```kotlin
    @Test
    fun `manual merge promotes a company name into org and exports it`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("name-company.vcf"), Source.APPLE) }
            assertEquals(2, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge
            onNodeWithText("+ Manual merge").performClick()
            onNodeWithText("Jane Smith", substring = true).performClick()
            onNodeWithText("Acme Inc", substring = true).performClick()
            onNodeWithText("Create merge").performClick()
            // auto-suggest: name = Jane Smith, org = Acme Inc (promoted from the company card's name)
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Deletion
            onNodeWithText("Next").performClick() // commit deletion -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertEquals(1, final.size)
            assertEquals("Acme Inc", final.single().org)

            val vcard = VcfExporter().export(final)
            assertTrue(vcard.contains("ORG:Acme Inc"), "exported vCard should carry the promoted org:\n$vcard")
            assertTrue(vcard.contains("FN:Jane Smith"), "exported vCard should carry the person name:\n$vcard")
        }
```

(`assertNotNull`, `assertTrue`, `assertEquals`, `runBlocking`, `Source`, `onNodeWithText`, `performClick`, `assertIsDisplayed` are already imported in `AppFlowTest.kt`.)

- [ ] **Step 3: Run the test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppFlowTest"`
Expected: PASS (all AppFlowTest cases).

If "Jane Smith"/"Acme Inc" aren't found in the picker, the matcher unexpectedly clustered them — verify `store.state.value.contacts` has 2 singletons; the names are distinct so they should not cluster. If `final.single().org` is not "Acme Inc", check that auto-suggest fired (no source has org; "Acme Inc" detects as LEGAL_SUFFIX). Do not weaken assertions.

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/fixtures/name-company.vcf \
        src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt
git commit -m "test(ui): e2e company-name promotion exports ORG (#29)"
```

---

### Task 9: Full gate, push, open PR

**Files:** none (verification + PR).

- [ ] **Step 1: Run the full gate**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — tests, Kover (line ≥90 / branch ≥70), Spotless, Konsist (core stays UI-free; `core/company` only imports `core.model`).
If Spotless fails: `./gradlew spotlessApply`, re-run `check`, amend the relevant commit.

- [ ] **Step 2: Push**

```bash
git push -u origin 29-name-and-company
```

- [ ] **Step 3: Open the PR**

```bash
gh pr create --base main --head 29-name-and-company \
  --title "Name + company (org) choice: detect & promote mis-filed company names (Closes #29)" \
  --body "Implements #29 Phase 1 per docs/superpowers/specs/2026-06-27-name-and-company-choice-design.md. Pure core CompanyNameDetector; orgChoice override applied in commit (engine + exporter untouched); auto-suggest promotes a mis-filed company name into org when no source has one; dedicated Company/org control + 'looks like a company' badge. Phase 2 (matcher linking) is separate. Closes #29."
```

- [ ] **Step 4: Watch CI**

Run: `gh pr checks <PR#> --watch`
Expected: `build` passes; hand off to the user for Claude App review + human merge.

---

## Self-Review

**Spec coverage:**
- §3 detector (4 signals, precision order, `core/company`) → Task 2. ✓
- §4 `orgChoice` field → Task 3. ✓
- §5 `chooseOrg` → Task 3; auto-suggest in `buildItems`+`manualMerge` (shared helper, org guard, highest-precision pick, person-name pick) → Task 5; org override in `commit` (empty → null) → Task 4. ✓
- §6 name badge → Task 6; Company/org control (orgs + promotions tagged "from name" + "(none)"; default = orgChoice ?: first source org ?: "") and skip org in generic conflicts → Task 7. ✓
- §2 export unchanged + asserted → Task 8 (asserts `ORG:Acme Inc`/`FN:Jane Smith` in the exported string). ✓
- §7 tests: detector table, store (chooseOrg/commit/auto-suggest), UI (badge/control/none/no-generic-org), e2e → Tasks 2–8. ✓
- §1 shared `displayName` needed by store+screen → Task 1 (refactor). ✓

**Placeholder scan:** none — every code/test step has full code and exact commands.

**Type consistency:** `CompanyNameDetector.detect(ContactName): CompanySignal?` used identically in detector, store (`companyAutoSuggest`), and screen (badge, `CompanyOrgField`). `chooseOrg(itemId, value: String)` and `ReviewItem.orgChoice: String?` consistent across store, tests, and the control. `reviewItem(id, origin, cluster)` signature matches its three call sites (high, uncertain, manual). `displayName(ContactName)` is the shared top-level function from Task 1, used by both `MergeScreen.kt` and `MergeReviewStore.kt`.

# Empty / Company-only Name Implementation Plan (#36)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a merged contact have no person name (company-only card), promote any source name to org, and recognize school-district companies — building on #29/#30.

**Architecture:** Add `ReviewItem.nameCleared`; a `clearName` intent; `commit()` empties the name when cleared; `companyAutoSuggest` clears the name for company-only clusters. The merge detail gets an unconditional "(no name)" Name option and lists every source name as a promotable org candidate. `CompanyNameDetector` gains "ISD" + "Independent School District". No matcher/merger/apply/exporter change.

**Tech Stack:** Kotlin, Compose Desktop Material (1.7.3), `runComposeUiTest`, Kover (line ≥90 / branch ≥70), Spotless/ktlint, Konsist.

Branch: `36-empty-company-name` (off `main`, already includes #29 + #30). Issue: #36. Spec: `docs/superpowers/specs/2026-06-27-empty-company-name-design.md`. Run tests with plain `./gradlew test` (macOS, no xvfb); Compose compile is slow.

## File Structure
- Modify `core/company/CompanyNameDetector.kt` — ISD keyword + Independent School District phrase.
- Modify `ui/MergeReviewTypes.kt` — `nameCleared`.
- Modify `ui/MergeReviewStore.kt` — `clearName`, `chooseName` reset, `companyAutoSuggest` (→ `AutoSuggest`), `reviewItem`, `commit` name-override.
- Modify `ui/MergeScreen.kt` — Name "(no name)" option; `CompanyOrgField` promotes all names.
- Tests: `CompanyNameDetectorTest`, `MergeReviewStoreCompanyTest`, `MergeScreenCompanyTest`, `AppFlowTest` + a fixture.

---

### Task 1: Detector — "ISD" + "Independent School District"

**Files:** Modify `src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt`; Test `src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetectorTest.kt`

- [ ] **Step 1: Add failing tests**

Append to `CompanyNameDetectorTest.kt` (inside the class):

```kotlin
    @Test
    fun `school districts are detected`() {
        assertEquals(CompanySignal.KEYWORD, CompanyNameDetector.detect(named(formatted = "Round Rock ISD")))
        assertEquals(
            CompanySignal.KEYWORD,
            CompanyNameDetector.detect(named(formatted = "Pflugerville Independent School District")),
        )
    }
```

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNameDetectorTest"` → FAIL ("Round Rock ISD" currently returns null).

- [ ] **Step 2: Implement**

In `CompanyNameDetector.kt`, add `"ISD"` to the `KEYWORDS` set (anywhere in the set literal). Then change the KEYWORD check line:

```kotlin
        if (cleaned.any { it in KEYWORDS }) return CompanySignal.KEYWORD
```

to also match the multi-word phrase:

```kotlin
        if (cleaned.any { it in KEYWORDS } || display.contains("independent school district", ignoreCase = true)) {
            return CompanySignal.KEYWORD
        }
```

(`display` is the local already computed at the top of `detect`. Keep this in the KEYWORD tier — after AMPERSAND, before WEAK.)

- [ ] **Step 3: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.company.CompanyNameDetectorTest"` → PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetector.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/company/CompanyNameDetectorTest.kt
git commit -m "feat(core): detect ISD / Independent School District as company (#36)"
```

---

### Task 2: Store — `nameCleared`, `clearName`, company-only auto-clear, commit empties name

**Files:** Modify `ui/MergeReviewTypes.kt`, `ui/MergeReviewStore.kt`; Test `ui/MergeReviewStoreCompanyTest.kt`

- [ ] **Step 1: Add failing tests**

Append to `MergeReviewStoreCompanyTest.kt` (the `orgCluster()` helper builds two "Pat Lee" cards both with `org = "Acme"`, shared phone — one HIGH cluster):

```kotlin
    @Test
    fun `clearName sets nameCleared and chooseName resets it`() {
        val store = orgCluster()
        val id = store.state.value.items.single().id
        store.clearName(id)
        assertEquals(true, store.state.value.items.single().nameCleared)
        store.chooseName(id, "a")
        assertEquals(false, store.state.value.items.single().nameCleared)
    }

    @Test
    fun `commit empties the merged name when cleared`() {
        val store = orgCluster()
        val id = store.state.value.items.single().id
        store.clearName(id)
        store.accept(id)
        val result = store.commit()
        assertEquals(com.robsartin.contactotomy.core.model.ContactName(), result.single().name)
    }

    @Test
    fun `company-only auto-suggest clears the name`() {
        // both members are company-like (KEYWORD), no org, shared phone => one HIGH cluster
        val c1 = contact("c1", given = "Bobs", family = "Plumbing", phones = listOf("+15125550000"))
        val c2 = contact("c2", given = "Bobs", family = "Plumbing", phones = listOf("+15125550000"))
        val item = MergeReviewStore(listOf(c1, c2)).state.value.items.single()
        assertEquals(true, item.nameCleared)
        assertNull(item.nameChoiceId)
        assertEquals("Bobs Plumbing", item.orgChoice)
    }

    @Test
    fun `person-plus-company auto-suggest does not clear the name`() {
        val jane = contact("jane", given = "Jane", family = "Smith", emails = listOf("jane@acme.com"))
        val acme = contact("acme", given = "Acme", family = "Inc", emails = listOf("info@acme.com"))
        val store = MergeReviewStore(listOf(jane, acme))
        val id = store.manualMerge(listOf("jane", "acme"))!!
        val item = store.state.value.items.first { it.id == id }
        assertEquals(false, item.nameCleared)
        assertEquals("jane", item.nameChoiceId)
    }
```

Also UPDATE the existing `buildItems auto-suggests org for a clustered company card with no org` test (Bobs Plumbing) — it currently asserts `orgChoice == "Bobs Plumbing"` and `assertNull(nameChoiceId)`. That still holds; no change needed there (the new `company-only auto-suggest clears the name` test covers `nameCleared`). If that existing test and the new one duplicate the same fixture, that is acceptable.

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"` → FAIL (`nameCleared`/`clearName` unresolved).

- [ ] **Step 2: Add the field**

In `MergeReviewTypes.kt`, add to `ReviewItem` (after `nameChoiceId`):

```kotlin
    val nameCleared: Boolean = false,
```

- [ ] **Step 3: Store intents + auto-suggest + commit**

In `MergeReviewStore.kt`:

(a) `clearName` intent + make `chooseName` reset the flag. Replace the existing `chooseName`:

```kotlin
    fun chooseName(
        itemId: String,
        memberId: String,
    ) = updateItem(itemId) { it.copy(nameChoiceId = memberId) }
```

with:

```kotlin
    fun chooseName(
        itemId: String,
        memberId: String,
    ) = updateItem(itemId) { it.copy(nameChoiceId = memberId, nameCleared = false) }

    fun clearName(itemId: String) = updateItem(itemId) { it.copy(nameCleared = true) }
```

(b) Change `companyAutoSuggest` to carry `nameCleared`. Replace the whole private `companyAutoSuggest` function with:

```kotlin
    private data class AutoSuggest(val nameChoiceId: String?, val nameCleared: Boolean, val orgChoice: String?)

    /**
     * When no member has an org but some member's name looks like a company, suggest promoting that
     * name into org. If a different member has a non-company name, use it as the person name;
     * otherwise (company-only) clear the name. Returns NO_SUGGEST when nothing applies.
     */
    private fun companyAutoSuggest(members: List<Contact>): AutoSuggest {
        if (members.any { !it.org.isNullOrBlank() }) return AutoSuggest(null, false, null)
        val companyMembers = members.mapNotNull { m -> CompanyNameDetector.detect(m.name)?.let { m to it } }
        if (companyMembers.isEmpty()) return AutoSuggest(null, false, null)
        val company = companyMembers.minBy { it.second.ordinal }.first
        val personId =
            members.firstOrNull {
                it.id != company.id && CompanyNameDetector.detect(it.name) == null && displayName(it.name).isNotBlank()
            }?.id
        return if (personId != null) {
            AutoSuggest(nameChoiceId = personId, nameCleared = false, orgChoice = displayName(company.name))
        } else {
            AutoSuggest(nameChoiceId = null, nameCleared = true, orgChoice = displayName(company.name))
        }
    }
```

(c) Update `reviewItem` to read the new fields. Replace its body:

```kotlin
        val (nameChoiceId, orgChoice) = companyAutoSuggest(cluster.members)
        return ReviewItem(
            id = id,
            origin = origin,
            proposal = merger.merge(cluster),
            nameChoiceId = nameChoiceId,
            orgChoice = orgChoice,
        )
```

with:

```kotlin
        val suggest = companyAutoSuggest(cluster.members)
        return ReviewItem(
            id = id,
            origin = origin,
            proposal = merger.merge(cluster),
            nameChoiceId = suggest.nameChoiceId,
            nameCleared = suggest.nameCleared,
            orgChoice = suggest.orgChoice,
        )
```

(d) `commit()` name-override: replace the `nameOverrides` block:

```kotlin
        val nameOverrides: Map<String, ContactName> =
            finalAccepted
                .mapNotNull { item ->
                    val memberId = item.nameChoiceId ?: return@mapNotNull null
                    val member =
                        item.proposal.cluster.members
                            .firstOrNull { it.id == memberId } ?: return@mapNotNull null
                    item.proposal.merged.id to member.name
                }.toMap()
```

with (cleared wins over choice):

```kotlin
        val nameOverrides: Map<String, ContactName> =
            finalAccepted
                .mapNotNull { item ->
                    when {
                        item.nameCleared -> item.proposal.merged.id to ContactName()
                        item.nameChoiceId != null -> {
                            val member =
                                item.proposal.cluster.members
                                    .firstOrNull { it.id == item.nameChoiceId }
                            member?.let { item.proposal.merged.id to it.name }
                        }
                        else -> null
                    }
                }.toMap()
```

(`ContactName` is already imported in this file.)

- [ ] **Step 4: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeReviewStoreCompanyTest"` → PASS.

- [ ] **Step 5: Full suite + spotless + commit**

Run `./gradlew test` (no regressions), then `./gradlew spotlessCheck` (apply + amend if needed).

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewTypes.kt \
        src/main/kotlin/com/robsartin/contactotomy/ui/MergeReviewStore.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeReviewStoreCompanyTest.kt
git commit -m "feat(ui): clearable name; company-only auto-clears name (#36)"
```

---

### Task 3: UI — "(no name)" Name option; promote any source name to org

**Files:** Modify `ui/MergeScreen.kt`; Test `ui/MergeScreenCompanyTest.kt`

- [ ] **Step 1: Add failing tests**

Append to `MergeScreenCompanyTest.kt` (it has `janeAcmeStore()` = jane [person] + acme [company], one manual item):

```kotlin
    @Test
    fun `name picker offers (no name) and selecting it clears the name`() =
        runComposeUiTest {
            val store = janeAcmeStore()
            setContent { MergeScreen(store) }
            onNodeWithText("(no name)").assertIsDisplayed()
            onNodeWithText("(no name)").performClick()
            assertEquals(true, store.state.value.items.single().nameCleared)
        }

    @Test
    fun `company-org control offers any source name (not just detected) for promotion`() =
        runComposeUiTest {
            val store = janeAcmeStore()
            setContent { MergeScreen(store) }
            // "Jane Smith" is NOT company-like, yet must still be promotable to org
            onNodeWithText("Jane Smith (from name)", substring = true).assertIsDisplayed()
        }
```

(Imports `assertEquals`, `performClick`, `onNodeWithText`, `runComposeUiTest`, `assertIsDisplayed` already present from prior tasks; add any missing.)

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenCompanyTest"` → FAIL (no "(no name)" node; "Jane Smith (from name)" not offered because the picker currently filters to detected names).

- [ ] **Step 2: Add "(no name)" to the Name group**

In `MergeScreen.kt` `MergeDetailContent`, replace the name block:

```kotlin
                if (namedMembers.isNotEmpty()) {
                    FieldGroup("Name (pick one)") {
                        namedMembers.forEach { m ->
                            val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                            val badge = if (CompanyNameDetector.detect(m.name) != null) "  · looks like a company" else ""
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = m.id == chosen, onClick = { store.chooseName(item.id, m.id) })
                                Text(displayName(m.name) + badge)
                            }
                        }
                    }
                }
```

with (source-name radios are deselected when cleared; add a "(no name)" radio):

```kotlin
                if (namedMembers.isNotEmpty()) {
                    FieldGroup("Name (pick one)") {
                        namedMembers.forEach { m ->
                            val chosen = item.nameChoiceId ?: defaultNameMemberId(p.cluster.members)
                            val selected = !item.nameCleared && m.id == chosen
                            val badge = if (CompanyNameDetector.detect(m.name) != null) "  · looks like a company" else ""
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { store.chooseName(item.id, m.id) })
                                Text(displayName(m.name) + badge)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = item.nameCleared, onClick = { store.clearName(item.id) })
                            Text("(no name)")
                        }
                    }
                }
```

- [ ] **Step 3: Promote any source name in `CompanyOrgField`**

In `CompanyOrgField`, replace the `promotions` computation:

```kotlin
    val promotions =
        members.mapNotNull { m ->
            if (CompanyNameDetector.detect(m.name) != null) displayName(m.name).takeIf { it.isNotBlank() } else null
        }
```

with (every non-blank source name is promotable; dedup against orgs happens via the `LinkedHashMap` below):

```kotlin
    val promotions = members.mapNotNull { m -> displayName(m.name).takeIf { it.isNotBlank() } }
```

(`CompanyNameDetector` may now be unused in `CompanyOrgField` — it is still used by the name badge in `MergeDetailContent`, so keep the file import.)

- [ ] **Step 4: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.MergeScreenCompanyTest"` → PASS.

- [ ] **Step 5: Run merge UI suite (guard against the new "(no name)"/promotion rows breaking selectors)**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.Merge*" --tests "com.robsartin.contactotomy.ui.AppFlowTest"`
Expected: PASS. Note: the Company/org picker now also lists person names as "(from name)" candidates — if any existing test asserted an exact promotion list, update it to the new set without weakening intent. The org picker still uses "(none)"; the name picker uses the distinct "(no name)" — they will not collide.

- [ ] **Step 6: Spotless + commit**

Run `./gradlew spotlessCheck` (apply + amend if needed).

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/MergeScreenCompanyTest.kt
git commit -m "feat(ui): (no name) option; promote any source name to org (#36)"
```

---

### Task 4: End-to-end — company-only cluster exports ORG with no name

**Files:** Create `src/test/resources/fixtures/round-rock-isd.vcf`; Modify `ui/AppFlowTest.kt`

- [ ] **Step 1: Create the fixture**

Create `src/test/resources/fixtures/round-rock-isd.vcf` (two identical school-district cards sharing an email → one HIGH cluster; no org):

```
BEGIN:VCARD
VERSION:3.0
N:ISD;Round;Rock;;
FN:Round Rock ISD
EMAIL;TYPE=INTERNET:info@rrisd.example
END:VCARD
BEGIN:VCARD
VERSION:3.0
N:ISD;Round;Rock;;
FN:Round Rock ISD
EMAIL;TYPE=INTERNET:info@rrisd.example
TEL;TYPE=CELL:+1 737-555-7777
END:VCARD
```

(The second card's phone uses area code 737, NOT 512, so the starter "austin area code" deletion rule does not flag it — the test never runs deletion, but this keeps the fixture unambiguous.)

- [ ] **Step 2: Add the test**

Add to `AppFlowTest.kt`. Ensure these imports exist (add any missing): `import com.robsartin.contactotomy.core.exporter.VcfExporter`, `import com.robsartin.contactotomy.core.model.ContactName`, `import androidx.compose.ui.test.onAllNodesWithText`, `import androidx.compose.ui.test.onFirst`, `import kotlin.test.assertFalse`.

```kotlin
    @Test
    fun `company-only cluster exports ORG with no name`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("round-rock-isd.vcf"), Source.APPLE) }
            assertEquals(2, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge (one HIGH cluster, auto-suggested company-only)
            onAllNodesWithText("Round Rock ISD", substring = true).onFirst().performClick() // select the cluster
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertEquals(1, final.size)
            assertEquals("Round Rock ISD", final.single().org)
            assertEquals(ContactName(), final.single().name)

            val vcard = VcfExporter().export(final)
            assertTrue(vcard.contains("ORG:Round Rock ISD"), "exported vCard should carry the org:\n$vcard")
            assertFalse(vcard.contains("FN:Round Rock ISD"), "company-only card should have no FN name:\n$vcard")
        }
```

- [ ] **Step 3: Run → PASS**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.AppFlowTest"`
Expected: PASS. If "Round Rock ISD" isn't found in the merge list, the two cards didn't cluster — confirm they share the email and identical name; do not weaken assertions.

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/fixtures/round-rock-isd.vcf src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt
git commit -m "test(ui): e2e company-only cluster exports ORG, no name (#36)"
```

---

### Task 5: Full gate, push, PR

**Files:** none.

- [ ] **Step 1: Full gate** — `./gradlew check` → BUILD SUCCESSFUL (tests, Kover 90/70, Spotless, Konsist). Fix Spotless via `spotlessApply` + amend if needed.
- [ ] **Step 2: Push** — `git push -u origin 36-empty-company-name`
- [ ] **Step 3: PR**

```bash
gh pr create --base main --head 36-empty-company-name \
  --title "Empty/company-only name: (no name) option, promote any name to org, ISD detection (Closes #36)" \
  --body "Implements #36 per docs/superpowers/specs/2026-06-27-empty-company-name-design.md. Adds an unconditional (no name) Name option (ReviewItem.nameCleared, commit empties the name), promotes ANY source name to org in the merge detail, auto-clears the name for company-only clusters, and detects ISD / Independent School District. No matcher/merger/apply/exporter change. Phase B (reviewed mark-as-company for standalone cards) is #37. Closes #36."
```

- [ ] **Step 4: Watch CI** — `gh pr checks <PR#> --watch`; on green hand off for the user's run-the-app visual review.

---

## Self-Review

**Spec coverage:**
- §2 ungated "(no name)" → Task 2 (`nameCleared`/`clearName`/commit) + Task 3 (UI radio). ✓
- §2 promote any source name → Task 3 (`CompanyOrgField` promotions). ✓
- §2 company-only auto-clear → Task 2 (`companyAutoSuggest`). ✓
- §2/§6 detector keywords (ISD + Independent School District) → Task 1. ✓
- §2 no matcher/merger/apply/exporter change → only the detector keyword list + ui changes. ✓
- §7 tests (detector, store, UI, e2e export-no-FN) → Tasks 1–4. ✓

**Placeholder scan:** none — full code in every step; the one selector-update caveat (Task 3 Step 5) gives concrete guidance, not "fix tests".

**Type consistency:** `nameCleared: Boolean` consistent across `ReviewItem`, store, UI, tests. `clearName(itemId)` matches its call site (`store.clearName(item.id)`). `companyAutoSuggest(members): AutoSuggest` (with `.nameChoiceId/.nameCleared/.orgChoice`) matches `reviewItem`'s usage. `commit` uses `ContactName()` for the empty name (matches the e2e assertion `ContactName()`). The Name option label "(no name)" is distinct from the org "(none)" so `onNodeWithText` stays unambiguous.

**Note:** label choice "(no name)" deliberately differs from the Company/org "(none)" to keep `onNodeWithText("(none)")` in existing MergeScreenCompanyTest unambiguous.

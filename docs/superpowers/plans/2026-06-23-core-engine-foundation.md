# Core Engine Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the Contactotomy Kotlin project and a headless core engine that imports Apple/Google vCard files into a normalized model and writes a combined cleaned vCard, with architecture boundaries enforced by Konsist.

**Architecture:** Pure-Kotlin core engine (`com.robsartin.contactotomy.core.*`) with no UI/Compose dependency. This plan builds the `model`, normalization utilities, `importer`, and `exporter`. Matching/merging (Plan 2), deletion rules (Plan 3), and the Compose UI (Plan 4) build on this foundation. See `docs/superpowers/specs/2026-06-23-contactotomy-design.md` and ADR-0002/0003/0006.

**Tech Stack:** Kotlin (JVM, toolchain 21), Gradle (Kotlin DSL), JUnit 5, ez-vcard (vCard parse/serialize), libphonenumber (phone normalization), Konsist (architecture tests).

---

## File Structure

- `settings.gradle.kts` — project name.
- `build.gradle.kts` — Kotlin JVM plugin, dependencies, test config.
- `gradle/wrapper/…`, `gradlew` — Gradle wrapper (generated).
- `src/main/kotlin/com/contactotomy/core/model/Contact.kt` — `Contact`, `ContactName`, `Source`.
- `src/main/kotlin/com/contactotomy/core/normalize/PhoneNormalizer.kt` — E.164 normalization.
- `src/main/kotlin/com/contactotomy/core/normalize/EmailNormalizer.kt` — email normalization.
- `src/main/kotlin/com/contactotomy/core/importer/VcfImporter.kt` — vCard → `List<Contact>`.
- `src/main/kotlin/com/contactotomy/core/exporter/VcfExporter.kt` — `List<Contact>` → vCard 3.0 string.
- `src/test/kotlin/com/contactotomy/core/…` — unit tests mirroring the above.
- `src/test/kotlin/com/contactotomy/architecture/ArchitectureTest.kt` — Konsist boundary rules.
- `src/test/resources/fixtures/*.vcf` — sample messy vCards.

Note: this plan keeps the project a single Gradle module with package-level boundaries (enforced by Konsist). The Compose UI in Plan 4 will live under `com.robsartin.contactotomy.ui.*` in the same module (or a split module if preferred then).

---

### Task 1: Gradle project scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `src/test/kotlin/com/contactotomy/core/SanityTest.kt`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
rootProject.name = "contactotomy"
```

- [ ] **Step 2: Create `build.gradle.kts`**

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.robsartin.contactotomy"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.googlecode.ez-vcard:ez-vcard:0.12.1")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.13.50")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testImplementation("com.lemonappdev:konsist:0.17.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 8.10.2`
Expected: creates `gradlew`, `gradle/wrapper/gradle-wrapper.properties`, and `gradle-wrapper.jar`.
(If `gradle` is not installed: `brew install gradle`, then re-run. After this task, always use `./gradlew`.)

- [ ] **Step 4: Write a sanity test**

`src/test/kotlin/com/contactotomy/core/SanityTest.kt`:

```kotlin
package com.robsartin.contactotomy.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SanityTest {
    @Test
    fun `toolchain runs tests`() {
        assertEquals(4, 2 + 2)
    }
}
```

- [ ] **Step 5: Run the sanity test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.SanityTest"`
Expected: BUILD SUCCESSFUL, 1 test passes. (This confirms the toolchain, dependencies resolve, and JUnit 5 is wired.)

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradlew gradlew.bat gradle/ src/test/kotlin/com/contactotomy/core/SanityTest.kt
git commit -m "chore: scaffold Kotlin/Gradle project with test toolchain"
```

---

### Task 2: Architecture boundary test (Konsist)

**Files:**
- Create: `src/test/kotlin/com/contactotomy/architecture/ArchitectureTest.kt`

This locks in ADR-0006 before there is much code to violate it.

- [ ] **Step 1: Write the failing architecture test**

`src/test/kotlin/com/contactotomy/architecture/ArchitectureTest.kt`:

```kotlin
package com.robsartin.contactotomy.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

class ArchitectureTest {

    @Test
    fun `core does not depend on ui or compose`() {
        Konsist.scopeFromProject()
            .files
            .filter { it.packagee?.fullyQualifiedName?.startsWith("com.robsartin.contactotomy.core") == true }
            .assertTrue { file ->
                file.imports.none { import ->
                    import.name.startsWith("androidx.compose") ||
                        import.name.startsWith("com.robsartin.contactotomy.ui")
                }
            }
    }
}
```

Note: Konsist's API surface can shift between versions. If `assertTrue`/`packagee.fullyQualifiedName`/`imports.name` differ in `0.17.3`, consult https://docs.konsist.lemonappdev.com and adjust — the rule itself ("no file under `com.robsartin.contactotomy.core` imports `androidx.compose.*` or `com.robsartin.contactotomy.ui.*`") is what must hold.

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.robsartin.contactotomy.architecture.ArchitectureTest"`
Expected: PASS (there is no UI code yet, so the rule holds vacuously). This verifies Konsist scans the project successfully.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/contactotomy/architecture/ArchitectureTest.kt
git commit -m "test: enforce core has no UI/Compose dependency via Konsist"
```

---

### Task 3: Contact model

**Files:**
- Create: `src/main/kotlin/com/contactotomy/core/model/Contact.kt`
- Test: `src/test/kotlin/com/contactotomy/core/model/ContactTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/contactotomy/core/model/ContactTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactTest {
    @Test
    fun `contact retains multi-value fields and source`() {
        val contact = Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Robert", middle = "A", family = "Sartin"),
            phones = listOf("+15125551234"),
            rawPhones = listOf("(512) 555-1234"),
            emails = listOf("rob@example.com"),
            rawVCard = "BEGIN:VCARD\nEND:VCARD",
        )

        assertEquals(Source.APPLE, contact.source)
        assertEquals("Robert", contact.name.given)
        assertEquals(listOf("+15125551234"), contact.phones)
        assertEquals(listOf("rob@example.com"), contact.emails)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.model.ContactTest"`
Expected: FAIL — `Contact` / `ContactName` / `Source` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/contactotomy/core/model/Contact.kt`:

```kotlin
package com.robsartin.contactotomy.core.model

import java.time.Instant

enum class Source { APPLE, GOOGLE, FILE }

data class ContactName(
    val prefix: String? = null,
    val given: String? = null,
    val middle: String? = null,
    val family: String? = null,
    val suffix: String? = null,
    val formatted: String? = null,
)

data class Contact(
    val id: String,
    val source: Source,
    val name: ContactName,
    val phones: List<String> = emptyList(),      // E.164-normalized
    val rawPhones: List<String> = emptyList(),   // original strings
    val emails: List<String> = emptyList(),      // lowercased, trimmed
    val addresses: List<String> = emptyList(),
    val org: String? = null,
    val title: String? = null,
    val urls: List<String> = emptyList(),
    val notes: String? = null,
    val categories: List<String> = emptyList(),  // Google labels (vCard CATEGORIES)
    val createdAt: Instant? = null,              // best-effort
    val modifiedAt: Instant? = null,             // best-effort (vCard REV)
    val rawVCard: String,
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.model.ContactTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/contactotomy/core/model/Contact.kt src/test/kotlin/com/contactotomy/core/model/ContactTest.kt
git commit -m "feat: add normalized Contact model"
```

---

### Task 4: Phone normalizer

**Files:**
- Create: `src/main/kotlin/com/contactotomy/core/normalize/PhoneNormalizer.kt`
- Test: `src/test/kotlin/com/contactotomy/core/normalize/PhoneNormalizerTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/contactotomy/core/normalize/PhoneNormalizerTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.normalize

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneNormalizerTest {
    private val normalizer = PhoneNormalizer(defaultRegion = "US")

    @Test
    fun `normalizes a US number to E164`() {
        assertEquals("+15125551234", normalizer.normalize("(512) 555-1234"))
    }

    @Test
    fun `keeps an already-international number`() {
        assertEquals("+442071234567", normalizer.normalize("+44 20 7123 4567"))
    }

    @Test
    fun `returns null for ungrokkable input`() {
        assertEquals(null, normalizer.normalize("not a phone"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.normalize.PhoneNormalizerTest"`
Expected: FAIL — `PhoneNormalizer` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/contactotomy/core/normalize/PhoneNormalizer.kt`:

```kotlin
package com.robsartin.contactotomy.core.normalize

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat

class PhoneNormalizer(private val defaultRegion: String = "US") {
    private val util = PhoneNumberUtil.getInstance()

    /** Returns the E.164 form, or null if the input cannot be parsed as a phone number. */
    fun normalize(raw: String): String? = try {
        val parsed = util.parse(raw, defaultRegion)
        if (util.isValidNumber(parsed)) util.format(parsed, PhoneNumberFormat.E164) else null
    } catch (e: NumberParseException) {
        null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.normalize.PhoneNormalizerTest"`
Expected: PASS (all 3).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/contactotomy/core/normalize/PhoneNormalizer.kt src/test/kotlin/com/contactotomy/core/normalize/PhoneNormalizerTest.kt
git commit -m "feat: add phone normalizer (E.164 via libphonenumber)"
```

---

### Task 5: Email normalizer

**Files:**
- Create: `src/main/kotlin/com/contactotomy/core/normalize/EmailNormalizer.kt`
- Test: `src/test/kotlin/com/contactotomy/core/normalize/EmailNormalizerTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/contactotomy/core/normalize/EmailNormalizerTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.normalize

import kotlin.test.Test
import kotlin.test.assertEquals

class EmailNormalizerTest {
    @Test
    fun `lowercases and trims`() {
        assertEquals("rob@example.com", EmailNormalizer.normalize("  Rob@Example.COM "))
    }

    @Test
    fun `returns null for blank`() {
        assertEquals(null, EmailNormalizer.normalize("   "))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.normalize.EmailNormalizerTest"`
Expected: FAIL — `EmailNormalizer` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/contactotomy/core/normalize/EmailNormalizer.kt`:

```kotlin
package com.robsartin.contactotomy.core.normalize

object EmailNormalizer {
    /** Lowercases and trims; returns null if blank. */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim().lowercase()
        return trimmed.ifEmpty { null }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.normalize.EmailNormalizerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/contactotomy/core/normalize/EmailNormalizer.kt src/test/kotlin/com/contactotomy/core/normalize/EmailNormalizerTest.kt
git commit -m "feat: add email normalizer"
```

---

### Task 6: vCard importer

**Files:**
- Create: `src/test/resources/fixtures/apple-sample.vcf`
- Create: `src/main/kotlin/com/contactotomy/core/importer/VcfImporter.kt`
- Test: `src/test/kotlin/com/contactotomy/core/importer/VcfImporterTest.kt`

- [ ] **Step 1: Create a fixture vCard**

`src/test/resources/fixtures/apple-sample.vcf`:

```
BEGIN:VCARD
VERSION:3.0
N:Sartin;Robert;A;;
FN:Robert A Sartin
TEL;TYPE=CELL:(512) 555-1234
EMAIL;TYPE=INTERNET:Rob@Example.COM
ORG:Acme Inc.
CATEGORIES:Work,Friends
REV:2024-01-15T10:30:00Z
END:VCARD
BEGIN:VCARD
VERSION:3.0
FN:No Phone Person
END:VCARD
```

- [ ] **Step 2: Write the failing test**

`src/test/kotlin/com/contactotomy/core/importer/VcfImporterTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcfImporterTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name"))
            .bufferedReader().use { it.readText() }

    @Test
    fun `imports cards with normalized fields and source tag`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))

        assertEquals(2, contacts.size)

        val rob = contacts.first()
        assertEquals(Source.APPLE, rob.source)
        assertEquals("Robert", rob.name.given)
        assertEquals("A", rob.name.middle)
        assertEquals("Sartin", rob.name.family)
        assertEquals(listOf("+15125551234"), rob.phones)        // normalized
        assertEquals(listOf("(512) 555-1234"), rob.rawPhones)   // original kept
        assertEquals(listOf("rob@example.com"), rob.emails)     // lowercased
        assertEquals("Acme Inc.", rob.org)
        assertEquals(listOf("Work", "Friends"), rob.categories)
        assertTrue(rob.rawVCard.contains("BEGIN:VCARD"))        // raw preserved
    }

    @Test
    fun `assigns stable distinct ids`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))
        assertEquals(contacts.size, contacts.map { it.id }.toSet().size)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.importer.VcfImporterTest"`
Expected: FAIL — `VcfImporter` unresolved.

- [ ] **Step 4: Write minimal implementation**

`src/main/kotlin/com/contactotomy/core/importer/VcfImporter.kt`:

```kotlin
package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.normalize.EmailNormalizer
import com.robsartin.contactotomy.core.normalize.PhoneNormalizer
import ezvcard.Ezvcard
import ezvcard.VCard
import java.time.Instant

class VcfImporter(
    private val source: Source,
    private val phoneNormalizer: PhoneNormalizer = PhoneNormalizer(),
) {
    /** Parses vCard text into normalized Contacts, tagging each with [source]. */
    fun import(vcfText: String): List<Contact> =
        Ezvcard.parse(vcfText).all().mapIndexed { index, card -> toContact(card, index) }

    private fun toContact(card: VCard, index: Int): Contact {
        val rawPhones = card.telephoneNumbers.mapNotNull { it.text }
        val rawEmails = card.emails.mapNotNull { it.value }
        return Contact(
            id = "${source.name.lowercase()}-$index",
            source = source,
            name = toName(card),
            phones = rawPhones.mapNotNull { phoneNormalizer.normalize(it) },
            rawPhones = rawPhones,
            emails = rawEmails.mapNotNull { EmailNormalizer.normalize(it) },
            addresses = card.addresses.mapNotNull { it.streetAddress },
            org = card.organization?.values?.joinToString(" / ")?.ifEmpty { null },
            title = card.titles.firstOrNull()?.value,
            urls = card.urls.mapNotNull { it.value },
            notes = card.notes.firstOrNull()?.value,
            categories = card.categories?.values?.toList() ?: emptyList(),
            modifiedAt = card.revision?.value?.let { Instant.ofEpochMilli(it.time) },
            rawVCard = Ezvcard.write(card).version(card.version ?: ezvcard.VCardVersion.V3_0).go(),
        )
    }

    private fun toName(card: VCard): ContactName {
        val n = card.structuredName
        return ContactName(
            prefix = n?.prefixes?.firstOrNull(),
            given = n?.given,
            middle = n?.additionalNames?.firstOrNull(),
            family = n?.family,
            suffix = n?.suffixes?.firstOrNull(),
            formatted = card.formattedName?.value,
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.importer.VcfImporterTest"`
Expected: PASS (both tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/contactotomy/core/importer/VcfImporter.kt src/test/kotlin/com/contactotomy/core/importer/VcfImporterTest.kt src/test/resources/fixtures/apple-sample.vcf
git commit -m "feat: add vCard importer (ez-vcard) producing normalized Contacts"
```

---

### Task 7: vCard exporter and round-trip

**Files:**
- Create: `src/main/kotlin/com/contactotomy/core/exporter/VcfExporter.kt`
- Test: `src/test/kotlin/com/contactotomy/core/exporter/VcfExporterTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/com/contactotomy/core/exporter/VcfExporterTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VcfExporterTest {
    @Test
    fun `writes vCard 3_0 with core fields`() {
        val contact = Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Robert", family = "Sartin", formatted = "Robert Sartin"),
            phones = listOf("+15125551234"),
            rawPhones = listOf("+15125551234"),
            emails = listOf("rob@example.com"),
            org = "Acme Inc.",
            categories = listOf("Work", "Friends"),
            rawVCard = "",
        )

        val text = VcfExporter().export(listOf(contact))

        assertTrue(text.contains("VERSION:3.0"))
        assertTrue(text.contains("FN:Robert Sartin"))
        assertTrue(text.contains("rob@example.com"))
        assertTrue(text.contains("+15125551234"))
        assertTrue(text.contains("Work") && text.contains("Friends"))
    }

    @Test
    fun `export then import round-trips core identity fields`() {
        val original = Contact(
            id = "1",
            source = Source.GOOGLE,
            name = ContactName(given = "Jane", middle = "Q", family = "Doe", formatted = "Jane Q Doe"),
            phones = listOf("+15125559999"),
            rawPhones = listOf("+15125559999"),
            emails = listOf("jane@example.com"),
            categories = listOf("Family"),
            rawVCard = "",
        )

        val text = VcfExporter().export(listOf(original))
        val reimported = com.robsartin.contactotomy.core.importer
            .VcfImporter(source = Source.GOOGLE).import(text).single()

        assertEquals("Jane", reimported.name.given)
        assertEquals("Doe", reimported.name.family)
        assertEquals(listOf("+15125559999"), reimported.phones)
        assertEquals(listOf("jane@example.com"), reimported.emails)
        assertEquals(listOf("Family"), reimported.categories)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.exporter.VcfExporterTest"`
Expected: FAIL — `VcfExporter` unresolved.

- [ ] **Step 3: Write minimal implementation**

`src/main/kotlin/com/contactotomy/core/exporter/VcfExporter.kt`:

```kotlin
package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.model.Contact
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.property.Categories
import ezvcard.property.StructuredName

class VcfExporter {
    /** Serializes contacts to a single vCard 3.0, UTF-8 string. */
    fun export(contacts: List<Contact>): String =
        Ezvcard.write(contacts.map { toVCard(it) }).version(VCardVersion.V3_0).go()

    private fun toVCard(contact: Contact): VCard {
        val card = VCard()

        val structured = StructuredName().apply {
            family = contact.name.family
            given = contact.name.given
            contact.name.middle?.let { additionalNames.add(it) }
            contact.name.prefix?.let { prefixes.add(it) }
            contact.name.suffix?.let { suffixes.add(it) }
        }
        card.structuredName = structured
        val fn = contact.name.formatted
            ?: listOfNotNull(contact.name.given, contact.name.family).joinToString(" ")
        if (fn.isNotBlank()) card.setFormattedName(fn)

        contact.phones.forEach { card.addTelephoneNumber(it) }
        contact.emails.forEach { card.addEmail(it) }
        contact.org?.let { card.setOrganization(it) }
        contact.title?.let { card.addTitle(it) }
        contact.urls.forEach { card.addUrl(it) }
        contact.notes?.let { card.addNote(it) }
        if (contact.categories.isNotEmpty()) {
            card.setCategories(Categories().apply { values.addAll(contact.categories) })
        }
        return card
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.core.exporter.VcfExporterTest"`
Expected: PASS (both tests).

- [ ] **Step 5: Run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all tests across Tasks 1–7 pass, including the Konsist architecture test.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/contactotomy/core/exporter/VcfExporter.kt src/test/kotlin/com/contactotomy/core/exporter/VcfExporterTest.kt
git commit -m "feat: add vCard 3.0 exporter with import round-trip"
```

---

## Self-Review

**Spec coverage (foundation slice):**
- Tech stack (spec §3 / ADR-0002): Task 1 wires Kotlin/Gradle/ez-vcard/libphonenumber/JUnit/Konsist. ✓
- Architecture boundary (spec §4 / ADR-0006): Task 2 Konsist test. ✓
- Data model (spec §5): Task 3 `Contact`/`ContactName`/`Source`, including multi-value fields, categories, best-effort dates, rawVCard. ✓
- Phone normalization to E.164, default US (spec §5/§6): Task 4. ✓
- Email lowercasing (spec §5): Task 5. ✓
- Import tagging source + preserving raw (spec §4 importer, ADR-0003): Task 6. ✓
- Export vCard 3.0, categories preserved, round-trip fidelity (spec §9, ADR-0004): Task 7. ✓
- **Out of scope for this plan (later plans):** matching (Plan 2), merging (Plan 2), deletion rules (Plan 3), UI (Plan 4), multi-file import orchestration & the in-app instructions (Plan 4). These are intentionally deferred.

**Placeholder scan:** No TBD/TODO; every code step contains complete code. The Konsist API note is a version-compatibility pointer, not a missing implementation.

**Type consistency:** `Contact`, `ContactName`, `Source` field names are used identically across Tasks 3, 6, and 7. `PhoneNormalizer.normalize` and `EmailNormalizer.normalize` signatures match their call sites in `VcfImporter`. `VcfExporter.export` / `VcfImporter.import` names are consistent in the round-trip test.

**Known follow-ups to confirm during execution:** ez-vcard accessor names (`structuredName`, `telephoneNumbers`, `categories.values`, `revision.value`) and Konsist `0.17.3` API should be verified against the resolved dependency versions; adjust the few accessor calls if a version differs, keeping behavior identical.

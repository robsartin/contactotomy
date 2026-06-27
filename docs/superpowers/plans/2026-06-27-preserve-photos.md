# Preserve Contact Photos Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve vCard `PHOTO` data end-to-end: import → `ContactPhoto` model → merge (primary wins) → export, so contact photos survive the clean.

**Architecture:** Add a library-free `ContactPhoto` data class to the model package (base64 String for embedded data, url String for external, contentType String). Wire it into `Contact` (nullable, defaults to null for full backward compat). `VcfImporter` reads `card.photos.firstOrNull()`, encodes embedded bytes to base64 or captures the URL. `VcfExporter` decodes and reconstructs an ez-vcard `Photo`. `ContactMerger` takes the primary member's photo, falling back to the first member with a non-null photo.

**Tech Stack:** Kotlin 2.0.21, ez-vcard 0.12.1 (`ezvcard.property.Photo`, `ezvcard.parameter.ImageType`), JUnit 5, Gradle 8.x with Kover (90% line / 70% branch).

## Global Constraints

- Pure TDD: write the failing test → confirm failure → implement minimally → confirm passing → commit.
- Model package (`core/model/`) must stay free of ez-vcard types (Konsist enforces this).
- Branch: `63-preserve-photos` off `main`.
- Coverage floors: line ≥ 90%, branch ≥ 70%. Every new branch (embedded vs url vs none; primary vs fallback) needs a test.
- Spotless/ktlint enforced: run `./gradlew spotlessApply` after every file edit before committing.
- YAGNI: one `ContactPhoto` per contact; no multi-photo modelling.
- Commit messages must end with: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- All commands run from the repo root `/Users/sartin/Contactotomy`.

---

### Task 1: Create branch and `ContactPhoto` model type

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/core/model/ContactPhoto.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/core/model/ContactPhotoTest.kt`

**Interfaces:**
- Produces: `data class ContactPhoto(val base64: String? = null, val url: String? = null, val contentType: String? = null)` — used by Tasks 2, 3, 4, 5.

- [ ] **Step 1: Create the branch**

```bash
git checkout main
git pull
git checkout -b 63-preserve-photos
```

Expected: `Switched to a new branch '63-preserve-photos'`

- [ ] **Step 2: Write the failing test**

Create `src/test/kotlin/com/robsartin/contactotomy/core/model/ContactPhotoTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactPhotoTest {
    @Test
    fun `ContactPhoto with base64 stores data and contentType`() {
        val photo = ContactPhoto(base64 = "abc123", contentType = "image/jpeg")
        assertEquals("abc123", photo.base64)
        assertEquals("image/jpeg", photo.contentType)
        assertNull(photo.url)
    }

    @Test
    fun `ContactPhoto with url stores url and contentType`() {
        val photo = ContactPhoto(url = "https://example.com/photo.png", contentType = "image/png")
        assertEquals("https://example.com/photo.png", photo.url)
        assertEquals("image/png", photo.contentType)
        assertNull(photo.base64)
    }

    @Test
    fun `ContactPhoto defaults all fields to null`() {
        val photo = ContactPhoto()
        assertNull(photo.base64)
        assertNull(photo.url)
        assertNull(photo.contentType)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.model.ContactPhotoTest" 2>&1 | tail -20
```

Expected: FAIL — `error: unresolved reference: ContactPhoto`

- [ ] **Step 4: Create the ContactPhoto model**

Create `src/main/kotlin/com/robsartin/contactotomy/core/model/ContactPhoto.kt`:

```kotlin
package com.robsartin.contactotomy.core.model

/**
 * An optional photo for a contact, stored as a base64-encoded string (if embedded)
 * or a URL (if external). Using a String (not ByteArray) gives value equality
 * and stays serialization-friendly.
 *
 * Exactly one of [base64] or [url] should be non-null in practice; both null is
 * valid (representing "no photo") and both non-null is treated as embedded-preferred.
 */
data class ContactPhoto(
    val base64: String? = null, // base64-encoded embedded image bytes, if embedded
    val url: String? = null, // external URL, if the PHOTO is a URI
    val contentType: String? = null, // e.g. "image/jpeg"
)
```

- [ ] **Step 5: Run test and verify it passes**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.model.ContactPhotoTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 6: Apply formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/model/ContactPhoto.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/model/ContactPhotoTest.kt
git commit -m "$(cat <<'EOF'
feat: add ContactPhoto model type for embedded/url photo data

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Add `photo` field to `Contact`

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/model/Contact.kt`
- Modify: `src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt`
- Modify: `src/test/kotlin/com/robsartin/contactotomy/core/model/ContactPhotoTest.kt` (add two new tests)

**Interfaces:**
- Consumes: `ContactPhoto` from Task 1.
- Produces: `Contact.photo: ContactPhoto? = null` — used by Tasks 3, 4, 5.

- [ ] **Step 1: Write the failing tests**

Add these two tests to `src/test/kotlin/com/robsartin/contactotomy/core/model/ContactPhotoTest.kt`.

The full updated `ContactPhotoTest.kt` (replace the entire file):

```kotlin
package com.robsartin.contactotomy.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactPhotoTest {
    @Test
    fun `ContactPhoto with base64 stores data and contentType`() {
        val photo = ContactPhoto(base64 = "abc123", contentType = "image/jpeg")
        assertEquals("abc123", photo.base64)
        assertEquals("image/jpeg", photo.contentType)
        assertNull(photo.url)
    }

    @Test
    fun `ContactPhoto with url stores url and contentType`() {
        val photo = ContactPhoto(url = "https://example.com/photo.png", contentType = "image/png")
        assertEquals("https://example.com/photo.png", photo.url)
        assertEquals("image/png", photo.contentType)
        assertNull(photo.base64)
    }

    @Test
    fun `ContactPhoto defaults all fields to null`() {
        val photo = ContactPhoto()
        assertNull(photo.base64)
        assertNull(photo.url)
        assertNull(photo.contentType)
    }

    @Test
    fun `Contact photo field defaults to null`() {
        val contact =
            Contact(
                id = "1",
                source = Source.APPLE,
                name = ContactName(given = "Alice"),
                rawVCard = "",
            )
        assertNull(contact.photo)
    }

    @Test
    fun `Contact stores a non-null ContactPhoto`() {
        val photo = ContactPhoto(base64 = "data", contentType = "image/jpeg")
        val contact =
            Contact(
                id = "1",
                source = Source.APPLE,
                name = ContactName(given = "Alice"),
                rawVCard = "",
                photo = photo,
            )
        assertEquals(photo, contact.photo)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.model.ContactPhotoTest" 2>&1 | tail -20
```

Expected: FAIL — `error: unresolved reference: photo` on the `Contact` constructor.

- [ ] **Step 3: Add `photo` field to Contact**

Replace the entire contents of `src/main/kotlin/com/robsartin/contactotomy/core/model/Contact.kt`:

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
    val phones: List<String> = emptyList(), // E.164-normalized
    val rawPhones: List<String> = emptyList(), // original strings
    val emails: List<String> = emptyList(), // lowercased, trimmed
    val addresses: List<PostalAddress> = emptyList(),
    val org: String? = null,
    val title: String? = null,
    val urls: List<String> = emptyList(),
    val notes: String? = null,
    val categories: List<String> = emptyList(), // Google labels (vCard CATEGORIES)
    val createdAt: Instant? = null, // best-effort
    val modifiedAt: Instant? = null, // best-effort (vCard REV)
    val photo: ContactPhoto? = null,
    val rawVCard: String,
)
```

- [ ] **Step 4: Add `photo` parameter to the testsupport `contact()` factory**

Replace the entire contents of `src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt`:

```kotlin
package com.robsartin.contactotomy.testsupport

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.ContactPhoto
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
    middle: String? = null,
    org: String? = null,
    notes: String? = null,
    categories: List<String> = emptyList(),
    modifiedAt: Instant? = null,
    createdAt: Instant? = null,
    source: Source = Source.APPLE,
    photo: ContactPhoto? = null,
) = Contact(
    id = id,
    source = source,
    name = ContactName(given = given, middle = middle, family = family),
    phones = phones,
    rawPhones = phones,
    emails = emails,
    org = org,
    notes = notes,
    categories = categories,
    modifiedAt = modifiedAt,
    createdAt = createdAt,
    photo = photo,
    rawVCard = "",
)
```

- [ ] **Step 5: Run test and verify it passes**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.model.ContactPhotoTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 6: Run full test suite to verify no regressions**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL — all existing tests still pass (the new `photo` field has `null` default).

- [ ] **Step 7: Apply formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/model/Contact.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/model/ContactPhotoTest.kt \
        src/test/kotlin/com/robsartin/contactotomy/testsupport/Contacts.kt
git commit -m "$(cat <<'EOF'
feat: add photo field to Contact model (nullable, backward-compat default)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Import PHOTO from vCard

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/importer/VcfImporter.kt`
- Create: `src/test/kotlin/com/robsartin/contactotomy/core/importer/VcfImporterPhotoTest.kt`
- Create: `src/test/resources/fixtures/photo-embedded.vcf`
- Create: `src/test/resources/fixtures/photo-url.vcf`

**Interfaces:**
- Consumes: `ContactPhoto` from Task 1; `Contact.photo` from Task 2; ez-vcard `Photo` (`card.photos.firstOrNull()`), `photo.data` (returns `byte[]`), `photo.url` (returns `String`), `photo.contentType` (returns `ImageType`).
- Produces: `VcfImporter.import(vcfText)` now populates `contact.photo` from the vCard `PHOTO` property.

- [ ] **Step 1: Create fixture files**

Create `src/test/resources/fixtures/photo-embedded.vcf`:

```
BEGIN:VCARD
VERSION:3.0
N:Smith;Alice;;;
FN:Alice Smith
PHOTO;ENCODING=b;TYPE=JPEG:/9j/4AAQSkZJRgAB
END:VCARD
```

Create `src/test/resources/fixtures/photo-url.vcf`:

```
BEGIN:VCARD
VERSION:3.0
N:Jones;Bob;;;
FN:Bob Jones
PHOTO;VALUE=URI:https://example.com/bob.jpg
END:VCARD
```

- [ ] **Step 2: Write the failing tests**

Create `src/test/kotlin/com/robsartin/contactotomy/core/importer/VcfImporterPhotoTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VcfImporterPhotoTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("fixtures/$name"))
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `imports embedded PHOTO into base64 field`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("photo-embedded.vcf"))
        val photo = contacts.single().photo
        assertNotNull(photo)
        assertTrue(photo.base64!!.isNotEmpty(), "base64 should be non-empty")
        assertNull(photo.url)
        assertEquals("image/jpeg", photo.contentType?.lowercase())
    }

    @Test
    fun `imports URL PHOTO into url field`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("photo-url.vcf"))
        val photo = contacts.single().photo
        assertNotNull(photo)
        assertEquals("https://example.com/bob.jpg", photo.url)
        assertNull(photo.base64)
    }

    @Test
    fun `vCard with no PHOTO yields null photo`() {
        val contacts = VcfImporter(source = Source.APPLE).import(fixture("apple-sample.vcf"))
        contacts.forEach { assertNull(it.photo) }
    }

    @Test
    fun `unknown PHOTO type is imported with best-effort contentType`() {
        val vcf =
            """
            BEGIN:VCARD
            VERSION:3.0
            N:Test;User;;;
            FN:User Test
            PHOTO;ENCODING=b;TYPE=BMP:Qk0=
            END:VCARD
            """.trimIndent()
        val contacts = VcfImporter(source = Source.APPLE).import(vcf)
        val photo = contacts.single().photo
        assertNotNull(photo)
        assertTrue(photo.base64!!.isNotEmpty())
        // contentType may be null or "image/bmp" — just verify no crash and photo populated
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.importer.VcfImporterPhotoTest" 2>&1 | tail -20
```

Expected: FAIL — `AssertionError: Expected value to not be null` (photo is always null until wired up).

- [ ] **Step 4: Implement photo import in VcfImporter**

Replace the entire contents of `src/main/kotlin/com/robsartin/contactotomy/core/importer/VcfImporter.kt`:

```kotlin
package com.robsartin.contactotomy.core.importer

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.normalize.EmailNormalizer
import com.robsartin.contactotomy.core.normalize.PhoneNormalizer
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import java.time.Instant
import java.util.Base64

class VcfImporter(
    private val source: Source,
    private val phoneNormalizer: PhoneNormalizer = PhoneNormalizer(),
) {
    /**
     * Parses vCard text into normalized Contacts, tagging each with [source].
     *
     * Note: the assigned [Contact.id] is positional (`"$source-$index"`) within a single import.
     * These ids are NOT stable across re-imports or reordering of the input, so downstream
     * consumers must not treat them as durable identity.
     */
    fun import(vcfText: String): List<Contact> = Ezvcard.parse(vcfText).all().mapIndexed { index, card -> toContact(card, index) }

    private fun toContact(
        card: VCard,
        index: Int,
    ): Contact {
        val rawPhones = card.telephoneNumbers.mapNotNull { it.text }
        val rawEmails = card.emails.mapNotNull { it.value }
        return Contact(
            id = "${source.name.lowercase()}-$index",
            source = source,
            name = toName(card),
            phones = rawPhones.mapNotNull { phoneNormalizer.normalize(it) },
            rawPhones = rawPhones,
            emails = rawEmails.mapNotNull { EmailNormalizer.normalize(it) },
            addresses = card.addresses.map { toPostalAddress(it) },
            org =
                card.organization
                    ?.values
                    ?.joinToString(" / ")
                    ?.ifEmpty { null },
            title = card.titles.firstOrNull()?.value,
            urls = card.urls.mapNotNull { it.value },
            notes = card.notes.firstOrNull()?.value,
            categories = card.categories?.values?.toList() ?: emptyList(),
            modifiedAt = card.revision?.value?.let { Instant.from(it) },
            photo = toContactPhoto(card),
            rawVCard = Ezvcard.write(card).version(card.version ?: VCardVersion.V3_0).go(),
        )
    }

    private fun imageTypeToMimeType(imageType: ezvcard.parameter.ImageType?): String? =
        when {
            imageType == null -> null
            imageType == ezvcard.parameter.ImageType.JPEG -> "image/jpeg"
            imageType == ezvcard.parameter.ImageType.PNG -> "image/png"
            imageType == ezvcard.parameter.ImageType.GIF -> "image/gif"
            else -> imageType.value?.let { "image/${it.lowercase()}" }
        }

    private fun toContactPhoto(card: VCard): ContactPhoto? {
        val photo = card.photos.firstOrNull() ?: return null
        val contentType = imageTypeToMimeType(photo.contentType)
        val data = photo.data
        return if (data != null && data.isNotEmpty()) {
            ContactPhoto(
                base64 = Base64.getEncoder().encodeToString(data),
                contentType = contentType,
            )
        } else {
            val url = photo.url ?: return null
            ContactPhoto(url = url, contentType = contentType)
        }
    }

    private fun toPostalAddress(addr: ezvcard.property.Address): PostalAddress =
        PostalAddress(
            poBox = addr.poBox?.takeIf { it.isNotEmpty() },
            extended = addr.extendedAddress?.takeIf { it.isNotEmpty() },
            street = addr.streetAddress?.takeIf { it.isNotEmpty() },
            city = addr.locality?.takeIf { it.isNotEmpty() },
            region = addr.region?.takeIf { it.isNotEmpty() },
            postalCode = addr.postalCode?.takeIf { it.isNotEmpty() },
            country = addr.country?.takeIf { it.isNotEmpty() },
        )

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

- [ ] **Step 5: Run photo import tests and verify they pass**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.importer.VcfImporterPhotoTest" 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 6: Run full test suite**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL — no regressions.

- [ ] **Step 7: Apply formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 8: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/importer/VcfImporter.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/importer/VcfImporterPhotoTest.kt \
        src/test/resources/fixtures/photo-embedded.vcf \
        src/test/resources/fixtures/photo-url.vcf
git commit -m "$(cat <<'EOF'
feat: import vCard PHOTO into ContactPhoto (embedded base64 and URL)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Export `ContactPhoto` to vCard

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/exporter/VcfExporter.kt`
- Create: `src/test/kotlin/com/robsartin/contactotomy/core/exporter/VcfExporterPhotoTest.kt`

**Interfaces:**
- Consumes: `ContactPhoto` from Task 1; `Contact.photo` from Task 2; ez-vcard constructors: `Photo(bytes: ByteArray, imageType: ImageType)` and `Photo(url: String, imageType: ImageType)`; `ImageType.JPEG`, `ImageType.PNG`, `ImageType.GIF`.
- Produces: `VcfExporter.export()` now writes a `PHOTO` property when `contact.photo != null`.

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/robsartin/contactotomy/core/exporter/VcfExporterPhotoTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.importer.VcfImporter
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VcfExporterPhotoTest {
    private val exporter = VcfExporter()
    private val importer = VcfImporter(source = Source.APPLE)

    private fun contactWithPhoto(photo: ContactPhoto?) =
        Contact(
            id = "1",
            source = Source.APPLE,
            name = ContactName(given = "Alice", family = "Smith"),
            rawVCard = "",
            photo = photo,
        )

    @Test
    fun `exports embedded photo as PHOTO property in vCard`() {
        val photo = ContactPhoto(base64 = "/9j/4A==", contentType = "image/jpeg")
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(text.contains("PHOTO"), "exported vCard should contain PHOTO line")
    }

    @Test
    fun `exports url photo as PHOTO URI property`() {
        val photo = ContactPhoto(url = "https://example.com/alice.jpg", contentType = "image/jpeg")
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(text.contains("PHOTO"), "exported vCard should contain PHOTO line")
        assertTrue(text.contains("example.com/alice.jpg"), "exported PHOTO should contain the URL")
    }

    @Test
    fun `contact with null photo exports no PHOTO property`() {
        val text = exporter.export(listOf(contactWithPhoto(null)))
        assertTrue(!text.contains("PHOTO"), "no PHOTO line expected when photo is null")
    }

    @Test
    fun `round-trip embedded photo preserves base64 data`() {
        val originalBytes = "hello photo".toByteArray()
        val originalBase64 = java.util.Base64.getEncoder().encodeToString(originalBytes)
        val photo = ContactPhoto(base64 = originalBase64, contentType = "image/jpeg")
        val exported = exporter.export(listOf(contactWithPhoto(photo)))
        val reimported = importer.import(exported).single()
        assertNotNull(reimported.photo)
        val reimportedBytes = java.util.Base64.getDecoder().decode(reimported.photo!!.base64)
        assertTrue(originalBytes.contentEquals(reimportedBytes), "round-trip should preserve photo bytes")
    }

    @Test
    fun `unknown contentType defaults to JPEG on export`() {
        val photo = ContactPhoto(base64 = "/9j/4A==", contentType = "image/bmp")
        val text = exporter.export(listOf(contactWithPhoto(photo)))
        assertTrue(text.contains("PHOTO"), "PHOTO line should still be written for unknown type")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.exporter.VcfExporterPhotoTest" 2>&1 | tail -20
```

Expected: FAIL — photo-related assertions fail (no PHOTO in output, URL missing).

- [ ] **Step 3: Implement photo export in VcfExporter**

Replace the entire contents of `src/main/kotlin/com/robsartin/contactotomy/core/exporter/VcfExporter.kt`:

```kotlin
package com.robsartin.contactotomy.core.exporter

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.PostalAddress
import ezvcard.Ezvcard
import ezvcard.VCard
import ezvcard.VCardVersion
import ezvcard.parameter.ImageType
import ezvcard.property.Categories
import ezvcard.property.Photo
import ezvcard.property.StructuredName
import java.util.Base64

class VcfExporter {
    /** Serializes contacts to a single vCard 3.0, UTF-8 string. */
    fun export(contacts: List<Contact>): String = Ezvcard.write(contacts.map { toVCard(it) }).version(VCardVersion.V3_0).go()

    private fun toVCard(contact: Contact): VCard {
        val card = VCard()

        val structured =
            StructuredName().apply {
                family = contact.name.family
                given = contact.name.given
                contact.name.middle?.let { additionalNames.add(it) }
                contact.name.prefix?.let { prefixes.add(it) }
                contact.name.suffix?.let { suffixes.add(it) }
            }
        card.structuredName = structured
        val fn =
            contact.name.formatted
                ?: listOfNotNull(contact.name.given, contact.name.family).joinToString(" ")
        if (fn.isNotBlank()) card.setFormattedName(fn)

        contact.phones.forEach { card.addTelephoneNumber(it) }
        contact.emails.forEach { card.addEmail(it) }
        contact.addresses.forEach { card.addAddress(toEzAddress(it)) }
        contact.org?.let { card.setOrganization(it) }
        contact.title?.let { card.addTitle(it) }
        contact.urls.forEach { card.addUrl(it) }
        contact.notes?.let { card.addNote(it) }
        if (contact.categories.isNotEmpty()) {
            card.setCategories(Categories().apply { values.addAll(contact.categories) })
        }
        contact.photo?.let { toEzPhoto(it)?.let { ezPhoto -> card.addPhoto(ezPhoto) } }
        return card
    }

    private fun mimeTypeToImageType(contentType: String?): ImageType =
        when (contentType?.lowercase()) {
            "image/jpeg", "image/jpg" -> ImageType.JPEG
            "image/png" -> ImageType.PNG
            "image/gif" -> ImageType.GIF
            else -> ImageType.JPEG // sensible default
        }

    private fun toEzPhoto(photo: ContactPhoto): Photo? {
        val imageType = mimeTypeToImageType(photo.contentType)
        return when {
            photo.base64 != null -> Photo(Base64.getDecoder().decode(photo.base64), imageType)
            photo.url != null -> Photo(photo.url, imageType)
            else -> null
        }
    }

    private fun toEzAddress(addr: PostalAddress): ezvcard.property.Address =
        ezvcard.property.Address().apply {
            addr.poBox?.let { poBox = it }
            addr.extended?.let { extendedAddress = it }
            addr.street?.let { streetAddress = it }
            addr.city?.let { locality = it }
            addr.region?.let { region = it }
            addr.postalCode?.let { postalCode = it }
            addr.country?.let { country = it }
        }
}
```

- [ ] **Step 4: Run photo export tests and verify they pass**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.exporter.VcfExporterPhotoTest" 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL, 5 tests passed.

- [ ] **Step 5: Run full test suite**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL — no regressions.

- [ ] **Step 6: Apply formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/exporter/VcfExporter.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/exporter/VcfExporterPhotoTest.kt
git commit -m "$(cat <<'EOF'
feat: export ContactPhoto to vCard PHOTO property (embedded and URL)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Preserve photo through merge

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt`
- Create: `src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerPhotoTest.kt`

**Interfaces:**
- Consumes: `ContactPhoto` from Task 1; `Contact.photo` from Task 2; `contact()` factory with `photo` param from Task 2. Primary = `ordered.first()` where `ordered` is sorted by `modifiedAt descending, then by id`.
- Produces: `ContactMerger.merge()` sets `merged.photo` = `ordered.firstNotNullOfOrNull { it.photo }` (primary's photo wins because primary is first in ordered; falls back to next member if primary has null photo).

- [ ] **Step 1: Write the failing tests**

Create `src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerPhotoTest.kt`:

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.matcher.Confidence
import com.robsartin.contactotomy.core.matcher.MatchReason
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.testsupport.contact
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactMergerPhotoTest {
    private val merger = ContactMerger()

    private fun cluster(vararg members: Contact) =
        Cluster("cluster-x", members.toList(), Confidence.HIGH, listOf(MatchReason.SHARED_PHONE))

    @Test
    fun `primary member photo is used when primary has a photo`() {
        val primaryPhoto = ContactPhoto(base64 = "primarydata", contentType = "image/jpeg")
        val otherPhoto = ContactPhoto(base64 = "otherdata", contentType = "image/jpeg")
        // primary = "b" because it has newer modifiedAt → sorted first
        val primary =
            contact(
                "b",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
                photo = primaryPhoto,
            )
        val other =
            contact(
                "a",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
                photo = otherPhoto,
            )
        val merged = merger.merge(cluster(primary, other)).merged
        assertEquals(primaryPhoto, merged.photo)
    }

    @Test
    fun `first member with photo is used when primary has no photo`() {
        val otherPhoto = ContactPhoto(url = "https://example.com/photo.jpg")
        val primary =
            contact(
                "b",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
                photo = null,
            )
        val other =
            contact(
                "a",
                given = "Alice",
                family = "Smith",
                modifiedAt = Instant.parse("2020-01-01T00:00:00Z"),
                photo = otherPhoto,
            )
        val merged = merger.merge(cluster(primary, other)).merged
        assertEquals(otherPhoto, merged.photo)
    }

    @Test
    fun `merged photo is null when no member has a photo`() {
        val a = contact("a", given = "Alice", family = "Smith", photo = null)
        val b = contact("b", given = "Alice", family = "Smith", photo = null)
        val merged = merger.merge(cluster(a, b)).merged
        assertNull(merged.photo)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.merger.ContactMergerPhotoTest" 2>&1 | tail -20
```

Expected: FAIL — `AssertionError: Expected <ContactPhoto(...)>, actual <null>`.

- [ ] **Step 3: Implement photo merge in ContactMerger**

Replace the entire contents of `src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt`:

```kotlin
package com.robsartin.contactotomy.core.merger

import com.robsartin.contactotomy.core.matcher.Cluster
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.toDisplayString
import java.time.Instant

/** Builds a MergeProposal for a cluster, with provenance, conflicts, and most-complete name. */
class ContactMerger {
    fun merge(cluster: Cluster): MergeProposal {
        val ordered =
            cluster.members.sortedWith(
                compareByDescending<Contact> { it.modifiedAt ?: Instant.MIN }.thenBy { it.id },
            )
        val primary = ordered.first()
        val name = mostCompleteName(ordered)

        val phones = union(ordered.map { it.phones })
        val rawPhones = union(ordered.map { it.rawPhones })
        val emails = union(ordered.map { it.emails })
        val addresses = unionOf(ordered.map { it.addresses })
        val urls = union(ordered.map { it.urls })
        val categories = union(ordered.map { it.categories })

        val org = ordered.firstNotNullOfOrNull { it.org }
        val title = ordered.firstNotNullOfOrNull { it.title }
        val notes = ordered.firstNotNullOfOrNull { it.notes }
        val photo = ordered.firstNotNullOfOrNull { it.photo }

        val merged =
            Contact(
                id = mergedId(cluster),
                source = primary.source,
                name = name,
                phones = phones,
                rawPhones = rawPhones,
                emails = emails,
                addresses = addresses,
                org = org,
                title = title,
                urls = urls,
                notes = notes,
                categories = categories,
                createdAt = cluster.members.mapNotNull { it.createdAt }.minOrNull(),
                modifiedAt = primary.modifiedAt,
                photo = photo,
                rawVCard = primary.rawVCard,
            )

        val provenance =
            buildList {
                addAll(multiProvenance("phones", phones, ordered) { it.phones })
                addAll(multiProvenance("emails", emails, ordered) { it.emails })
                addAll(addressProvenance(addresses, ordered))
                addAll(multiProvenance("urls", urls, ordered) { it.urls })
                addAll(multiProvenance("categories", categories, ordered) { it.categories })
                org?.let { add(singleProvenance("org", it, ordered) { c -> c.org }) }
                title?.let { add(singleProvenance("title", it, ordered) { c -> c.title }) }
                notes?.let { add(singleProvenance("notes", it, ordered) { c -> c.notes }) }
            }

        val conflicts =
            listOfNotNull(
                conflictFor("org", ordered) { it.org },
                conflictFor("title", ordered) { it.title },
                conflictFor("notes", ordered) { it.notes },
            )

        return MergeProposal(cluster, merged, provenance, conflicts)
    }

    private fun mostCompleteName(ordered: List<Contact>): ContactName =
        ordered.maxByOrNull { completeness(it.name) }?.name ?: ordered.first().name

    private fun completeness(n: ContactName): Int = listOf(n.prefix, n.given, n.middle, n.family, n.suffix).count { !it.isNullOrBlank() }

    private fun multiProvenance(
        field: String,
        values: List<String>,
        members: List<Contact>,
        getter: (Contact) -> List<String>,
    ): List<FieldProvenance> =
        values.map { value ->
            FieldProvenance(field, value, members.filter { value in getter(it) }.map { it.id })
        }

    private fun singleProvenance(
        field: String,
        value: String,
        members: List<Contact>,
        getter: (Contact) -> String?,
    ): FieldProvenance = FieldProvenance(field, value, members.filter { getter(it) == value }.map { it.id })

    private fun conflictFor(
        field: String,
        ordered: List<Contact>,
        getter: (Contact) -> String?,
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

    private fun unionOf(lists: List<List<PostalAddress>>): List<PostalAddress> {
        val out = LinkedHashSet<PostalAddress>()
        lists.forEach { out.addAll(it) }
        return out.toList()
    }

    private fun addressProvenance(
        addresses: List<PostalAddress>,
        members: List<Contact>,
    ): List<FieldProvenance> =
        addresses.map { addr ->
            FieldProvenance(
                "addresses",
                addr.toDisplayString(),
                members.filter { addr in it.addresses }.map { it.id },
            )
        }

    private fun mergedId(cluster: Cluster): String =
        "merged-" +
            cluster.members
                .map { it.id }
                .sorted()
                .joinToString("+")
}
```

- [ ] **Step 4: Run photo merger tests and verify they pass**

```bash
./gradlew test --tests "com.robsartin.contactotomy.core.merger.ContactMergerPhotoTest" 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL, 3 tests passed.

- [ ] **Step 5: Run full test suite**

```bash
./gradlew test 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL — all tests pass.

- [ ] **Step 6: Apply formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/core/merger/ContactMerger.kt \
        src/test/kotlin/com/robsartin/contactotomy/core/merger/ContactMergerPhotoTest.kt
git commit -m "$(cat <<'EOF'
feat: preserve photo through merge (primary member's photo wins)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Gate — run `./gradlew check` and open PR

**Files:** None (CI gate only).

- [ ] **Step 1: Run the full quality gate**

```bash
./gradlew check 2>&1 | tail -50
```

Expected: BUILD SUCCESSFUL. All tasks: `test`, `koverVerify` (≥90% line, ≥70% branch), `spotlessCheck`, Konsist architecture tests — all green.

If coverage fails, the most likely uncovered branch is `toEzPhoto`'s `else -> null` (both base64 and url are null). Add this test to `VcfExporterPhotoTest`:

```kotlin
@Test
fun `ContactPhoto with neither base64 nor url exports no PHOTO`() {
    val photo = ContactPhoto(contentType = "image/jpeg") // both base64 and url are null
    val text = exporter.export(listOf(contactWithPhoto(photo)))
    assertTrue(!text.contains("PHOTO"), "no PHOTO line when neither base64 nor url is set")
}
```

Then re-run `./gradlew check`.

- [ ] **Step 2: If spotlessCheck reports any formatting issues**

```bash
./gradlew spotlessApply && ./gradlew check 2>&1 | tail -30
```

- [ ] **Step 3: Push the branch**

```bash
git push -u origin 63-preserve-photos
```

- [ ] **Step 4: Open the PR**

```bash
gh pr create \
  --title "feat: preserve vCard PHOTO data end-to-end (Closes #63)" \
  --body "$(cat <<'EOF'
## Summary

- Adds `ContactPhoto` model type (base64 String, url, contentType) — library-free, value-equality safe
- Adds `photo: ContactPhoto? = null` field to `Contact` (backward-compat null default)
- `VcfImporter` reads `card.photos.firstOrNull()`, encodes embedded bytes to base64 or captures URL; maps `ImageType` to MIME string
- `VcfExporter` decodes base64 back to bytes and constructs `ezvcard.property.Photo(bytes, ImageType)` (or URL variant via `Photo(url, ImageType)`)
- `ContactMerger` selects the primary member's photo (newest by modifiedAt first), falling back to the first non-null photo across members
- Round-trip test: import embedded photo → export → re-import → bytes survive unchanged

Closes #63

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

**Spec coverage:**

| Requirement | Task |
|---|---|
| `ContactPhoto` model (base64, url, contentType) | Task 1 |
| `photo: ContactPhoto? = null` on `Contact` | Task 2 |
| Importer: embedded → base64 | Task 3 |
| Importer: URL → url | Task 3 |
| Importer: no photo → null | Task 3 |
| Exporter: embedded → `Photo(bytes, ImageType)` | Task 4 |
| Exporter: URL → `Photo(url, ImageType)` | Task 4 |
| Round-trip test | Task 4 |
| Merger: primary's photo wins | Task 5 |
| Merger: fallback to first member with photo | Task 5 |
| Merger: null when none have photo | Task 5 |
| `./gradlew check` green | Task 6 |
| PR to main with `Closes #63` | Task 6 |

**Placeholder scan:** None found. All steps have actual code or commands.

**Type consistency:**
- `ContactPhoto` defined in Task 1, used consistently as `ContactPhoto` throughout Tasks 2–5.
- `contact()` factory's `photo` param (Task 2) used in Task 5 tests as `contact(..., photo = ...)`.
- `card.addPhoto(ezPhoto)` — verified as the ez-vcard API for adding a `Photo` to a `VCard`.
- `Photo(bytes: ByteArray, imageType: ImageType)` and `Photo(url: String, imageType: ImageType)` — constructors verified from javap.
- `ImageType.JPEG`, `ImageType.PNG`, `ImageType.GIF` — static constants verified from javap.
- `photo.data` (returns `byte[]`), `photo.url` (returns `String`), `photo.contentType` (returns `ImageType`) — inherited from `BinaryProperty`, verified from javap.

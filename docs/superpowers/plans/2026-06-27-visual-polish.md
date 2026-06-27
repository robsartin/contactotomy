# Visual Polish Implementation Plan (#30)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring all four Compose screens up to the approved mockup fidelity via a shared theme + reusable styled components — presentation only, no behavior change.

**Architecture:** A `ContactotomyTheme` provides a Material `Colors` plus an `AppColors` (domain colors) over a `CompositionLocal`. Small reusable components (`SourceBadge`, `ConfidenceChip`, `SourceCard`, `ClusterRow`, `LabeledProgress`, `FieldGroup`, `ValueChip`, `SectionHeader`) live in `ui/components/`. The four screen composables are restyled to use them. Stores, intents, and `core` are untouched; multi-value merge fields switch from `Checkbox` to `FilterChip` (`ValueChip`) with `testTag`s so selectors stay valid.

**Tech Stack:** Kotlin, Compose Desktop Material (1.7.3), `runComposeUiTest`, Kover (line ≥90 / branch ≥70), Spotless/ktlint, Konsist.

Branch: `30-visual-polish` (off `main`). Issue: #30. Spec: `docs/superpowers/specs/2026-06-27-visual-polish-design.md`.
Run tests with plain `./gradlew test` on macOS (no xvfb). Compose compile is slow (minutes) — normal.

## File Structure

- Create `ui/theme/AppColors.kt` — `AppColors` data class + `LocalAppColors` + `appColors` accessor.
- Create `ui/theme/Dimens.kt` — padding/radius/border `Dp` constants.
- Create `ui/theme/Theme.kt` — `ContactotomyTheme { }`.
- Create `ui/components/Badges.kt` — `SourceBadge`, `ConfidenceChip`.
- Create `ui/components/Cards.kt` — `SourceCard`, `ClusterRow`.
- Create `ui/components/Fields.kt` — `LabeledProgress`, `FieldGroup`, `SectionHeader`, `ValueChip`.
- Modify `ui/App.kt` (wrap in theme), `ui/MergeScreen.kt`, `ui/ImportScreen.kt`, `ui/ExportScreen.kt`, `ui/DeletionScreen.kt`.
- Tests: `ui/components/*Test.kt`; update merge UI tests for the chip swap.

Convention used throughout: `import androidx.compose.ui.unit.sp` for font sizes; colors via `appColors`; reuse the shared `displayName(ContactName)` from `ui/ContactDisplay.kt`.

---

### Task 1: Theme — `AppColors`, `Dimens`, `ContactotomyTheme`; wrap `App`

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/theme/AppColors.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/theme/Dimens.kt`
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/theme/Theme.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/App.kt`

- [ ] **Step 1: Create `AppColors.kt`**

```kotlin
package com.robsartin.contactotomy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App-specific colors Material's [androidx.compose.material.Colors] does not model. */
data class AppColors(
    val accent: Color = Color(0xFF4A90D9),
    val appleBadge: Color = Color(0xFF4A90D9),
    val googleBadge: Color = Color(0xFF777777),
    val highChipBg: Color = Color(0xFFE7F0FF),
    val highChipFg: Color = Color(0xFF185FA5),
    val maybeChipBg: Color = Color(0xFFFFF3D6),
    val maybeChipFg: Color = Color(0xFF854F0B),
    val manualChipBg: Color = Color(0xFFEDEDEA),
    val manualChipFg: Color = Color(0xFF5F5E5A),
    val maybeCardBg: Color = Color(0xFFFFFDF5),
    val maybeCardBorder: Color = Color(0xFFE0C070),
    val cardBorder: Color = Color(0xFFD8D8D4),
    val selectedBorder: Color = Color(0xFF4A90D9),
    val star: Color = Color(0xFFE8A000),
    val accept: Color = Color(0xFF2E7D32),
    val reject: Color = Color(0xFFC62828),
    val mergedBorder: Color = Color(0xFF2E7D32),
    val muted: Color = Color(0xFF999999),
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

val appColors: AppColors
    @Composable @ReadOnlyComposable
    get() = LocalAppColors.current
```

- [ ] **Step 2: Create `Dimens.kt`**

```kotlin
package com.robsartin.contactotomy.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val cardRadius = 10.dp
    val chipRadius = 12.dp
    val hairline = 1.dp
    val selected = 2.dp
}
```

- [ ] **Step 3: Create `Theme.kt`**

```kotlin
package com.robsartin.contactotomy.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Wraps MaterialTheme with the app palette and provides [AppColors]. */
@Composable
fun ContactotomyTheme(content: @Composable () -> Unit) {
    val app = AppColors()
    val colors =
        lightColors(
            primary = app.accent,
            secondary = app.accent,
            error = app.reject,
        )
    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialTheme(colors = colors, content = content)
    }
}
```

- [ ] **Step 4: Wrap `App` in the theme**

In `App.kt`, replace the `MaterialTheme {` wrapper with `ContactotomyTheme {`. Change the import `import androidx.compose.material.MaterialTheme` to `import com.robsartin.contactotomy.ui.theme.ContactotomyTheme`. The body inside is unchanged.

- [ ] **Step 5: Run the suite to confirm no behavior change**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all existing tests pass (theme wrapping is transparent; `AppShellTest`/`AppNavigationTest` still find their nodes).

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/theme src/main/kotlin/com/robsartin/contactotomy/ui/App.kt
git commit -m "feat(ui): ContactotomyTheme + AppColors/Dimens (#30)"
```

---

### Task 2: `SourceBadge` + `ConfidenceChip`

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/components/Badges.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/components/BadgesTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.ui.Origin
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BadgesTest {
    @Test
    fun `source badge shows the provider label`() =
        runComposeUiTest {
            setContent { SourceBadge(Source.GOOGLE) }
            onNodeWithText("Google").assertIsDisplayed()
        }

    @Test
    fun `confidence chip label by origin`() =
        runComposeUiTest {
            setContent { ConfidenceChip(Origin.UNCERTAIN) }
            onNodeWithText("maybe").assertIsDisplayed()
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.components.BadgesTest"`
Expected: FAIL — unresolved `SourceBadge`/`ConfidenceChip`.

- [ ] **Step 3: Implement `Badges.kt`**

```kotlin
package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.ui.Origin
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun SourceBadge(source: Source) {
    val c = appColors
    val (bg, label) =
        when (source) {
            Source.APPLE -> c.appleBadge to "Apple"
            Source.GOOGLE -> c.googleBadge to "Google"
            Source.FILE -> c.googleBadge to "File"
        }
    Text(
        label,
        color = Color.White,
        fontSize = 10.sp,
        modifier =
            Modifier
                .background(bg, RoundedCornerShape(Dimens.chipRadius))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ConfidenceChip(origin: Origin) {
    val c = appColors
    val (bg, fg, label) =
        when (origin) {
            Origin.HIGH -> Triple(c.highChipBg, c.highChipFg, "HIGH")
            Origin.UNCERTAIN -> Triple(c.maybeChipBg, c.maybeChipFg, "maybe")
            Origin.MANUAL -> Triple(c.manualChipBg, c.manualChipFg, "manual")
        }
    Text(
        label,
        color = fg,
        fontSize = 10.sp,
        modifier =
            Modifier
                .background(bg, RoundedCornerShape(Dimens.chipRadius))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.components.BadgesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/components/Badges.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/components/BadgesTest.kt
git commit -m "feat(ui): SourceBadge + ConfidenceChip components (#30)"
```

---

### Task 3: `LabeledProgress`, `FieldGroup`, `SectionHeader`, `ValueChip`

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/components/Fields.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/components/FieldsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FieldsTest {
    @Test
    fun `labeled progress shows reviewed of total`() =
        runComposeUiTest {
            setContent { LabeledProgress(reviewed = 2, total = 6) }
            onNodeWithText("2 of 6 reviewed").assertIsDisplayed()
        }

    @Test
    fun `value chip toggle fires its callback`() =
        runComposeUiTest {
            var toggled = false
            setContent { ValueChip(label = "x@y.com", selected = true, onToggle = { toggled = true }, tag = "emails:x@y.com") }
            onNodeWithTag("emails:x@y.com").performClick()
            assertTrue(toggled)
        }

    @Test
    fun `field group renders its header`() =
        runComposeUiTest {
            setContent { FieldGroup("Phones (keep any)") { } }
            onNodeWithText("Phones (keep any)").assertIsDisplayed()
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.components.FieldsTest"`
Expected: FAIL — unresolved components.

- [ ] **Step 3: Implement `Fields.kt`**

Note: `FieldGroup` does NOT uppercase its header string (keeps text selectors stable); it styles it small + muted with letter spacing for the "header" look.

```kotlin
package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun SectionHeader(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = Dimens.sm))
}

@Composable
fun FieldGroup(
    header: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = Dimens.sm)) {
        Text(header, fontSize = 10.sp, color = appColors.muted, letterSpacing = 0.08.em)
        content()
    }
}

@Composable
fun LabeledProgress(
    reviewed: Int,
    total: Int,
) {
    val fraction = if (total == 0) 0f else reviewed.toFloat() / total.toFloat()
    Column {
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth().height(6.dp))
        Text("$reviewed of $total reviewed", fontSize = 10.sp, color = appColors.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ValueChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    tag: String,
    fontSize: TextUnit = 11.sp,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        modifier = Modifier.testTag(tag),
        colors = ChipDefaults.filterChipColors(),
    ) {
        Text(label, fontSize = fontSize)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.components.FieldsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/components/Fields.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/components/FieldsTest.kt
git commit -m "feat(ui): LabeledProgress, FieldGroup, SectionHeader, ValueChip (#30)"
```

---

### Task 4: `SourceCard` + `ClusterRow`

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/components/Cards.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/components/CardsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CardsTest {
    private val rob =
        Contact(
            id = "a",
            source = Source.APPLE,
            name = ContactName(given = "Rob", family = "Sartin"),
            phones = listOf("+15125551234"),
            rawVCard = "",
        )

    @Test
    fun `source card shows name and provider badge`() =
        runComposeUiTest {
            setContent { SourceCard(rob, primary = true, selected = true) }
            onNodeWithText("Rob Sartin").assertIsDisplayed()
            onNodeWithText("Apple").assertIsDisplayed()
        }

    @Test
    fun `cluster row click fires onClick`() =
        runComposeUiTest {
            var clicked = false
            setContent { ClusterRow(title = "Rob Sartin · 2 cards", origin = com.robsartin.contactotomy.ui.Origin.HIGH, selected = false, onClick = { clicked = true }) }
            onNodeWithText("Rob Sartin · 2 cards").performClick()
            assertTrue(clicked)
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.components.CardsTest"`
Expected: FAIL — unresolved `SourceCard`/`ClusterRow`.

- [ ] **Step 3: Implement `Cards.kt`**

```kotlin
package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.ui.Origin
import com.robsartin.contactotomy.ui.displayName
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun SourceCard(
    contact: Contact,
    primary: Boolean,
    selected: Boolean,
) {
    val c = appColors
    val border = if (selected) BorderStroke(Dimens.selected, c.selectedBorder) else BorderStroke(Dimens.hairline, c.cardBorder)
    Card(border = border, shape = RoundedCornerShape(Dimens.cardRadius), modifier = Modifier.padding(end = 6.dp)) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(contact.source)
                if (primary) Text("  ★", color = c.star, fontSize = 11.sp)
                Text(
                    contact.modifiedAt?.toString()?.take(10) ?: "—",
                    color = c.muted,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                )
            }
            Text(displayName(contact.name), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            val line = (contact.phones + contact.emails + listOfNotNull(contact.org)).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, color = c.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun ClusterRow(
    title: String,
    origin: Origin,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = appColors
    val border =
        when {
            selected -> BorderStroke(Dimens.selected, c.selectedBorder)
            origin == Origin.UNCERTAIN -> BorderStroke(Dimens.hairline, c.maybeCardBorder)
            else -> BorderStroke(Dimens.hairline, c.cardBorder)
        }
    Card(
        border = border,
        shape = RoundedCornerShape(Dimens.cardRadius),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() },
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.fillMaxWidth(0.78f))
            ConfidenceChip(origin)
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.components.CardsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/components/Cards.kt \
        src/test/kotlin/com/robsartin/contactotomy/ui/components/CardsTest.kt
git commit -m "feat(ui): SourceCard + ClusterRow components (#30)"
```

---

### Task 5: Restyle `MergeScreen` with the components (incl. Checkbox→ValueChip)

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt`
- Modify (as needed): `MergeDetailTest.kt`, `MergeScreenListTest.kt`, `MergeBeforeCardsTest.kt`, `MergeScreenCompanyTest.kt`, `MergeScreenManualMergeTest.kt`, `AppFlowTest.kt`, `AppNavigationTest.kt`

This is the centerpiece. Keep ALL store calls and all user-visible button/label TEXT identical (e.g. "Accept merge", "Keep separate", "Needs review (N)", "Accept all high-confidence", "+ Manual merge", "Create merge", "Company / org (pick one)", radio label texts). Only the rendering of rows/cards/multi-value controls changes.

- [ ] **Step 1: Restyle the left list — use `ClusterRow` + `LabeledProgress`**

In `MergeScreen`, the left column currently renders the header, a "X of Y reviewed" `Text`, a `LazyColumn` of `ClusterRow`-via-`Button` rows, and the "Will merge…" footer. Replace the private `ClusterRow` composable (the `Button`-based one at the bottom of the file) usage with the new component, and swap the plain progress text for `LabeledProgress`.

Replace the progress line:
```kotlin
            Text("${resolved.size} of ${state.items.size} reviewed", Modifier.padding(vertical = 4.dp))
```
with:
```kotlin
            com.robsartin.contactotomy.ui.components.LabeledProgress(reviewed = resolved.size, total = state.items.size)
```

Replace the `items(pending) { item -> ClusterRow(...) }` call so it uses the new component (build the same label text as today):
```kotlin
                items(pending) { item ->
                    val tag =
                        when (item.origin) {
                            Origin.UNCERTAIN -> "maybe"
                            Origin.MANUAL -> "manual"
                            Origin.HIGH -> "HIGH"
                        }
                    val label =
                        if (item.origin == Origin.UNCERTAIN) {
                            item.proposal.cluster.members.joinToString(" ↔ ") { displayName(it.name) } +
                                " · " + item.proposal.cluster.reasons.joinToString(", ")
                        } else {
                            "${displayName(item.proposal.merged.name)} · ${item.proposal.cluster.members.size} cards"
                        }
                    com.robsartin.contactotomy.ui.components.ClusterRow(
                        title = label,
                        origin = item.origin,
                        selected = item.id == selected?.id,
                        onClick = { selectedId = item.id },
                    )
                }
```
Delete the now-unused private `ClusterRow` composable (the one wrapping a `Button`) and the now-unused `tag` local that referenced it. Keep `ResolvedRow` as-is (or lightly restyle; not required). The `[$tag]` text is dropped because the `ConfidenceChip` now shows it — this changes a row's text; update any test that matched `"[HIGH]"`/`"[manual]"` (see Step 4).

- [ ] **Step 2: Restyle the detail — `SourceCards` → `SourceCard`s**

Replace the private `SourceCards` composable body so each member renders via the new `SourceCard`:
```kotlin
@Composable
private fun SourceCards(members: List<Contact>) {
    val sorted = members.sortedWith(compareByDescending(nullsLast()) { it.modifiedAt })
    com.robsartin.contactotomy.ui.components.SectionHeader("Source cards (${sorted.size})")
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        sorted.forEachIndexed { index, m ->
            androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                com.robsartin.contactotomy.ui.components.SourceCard(contact = m, primary = index == 0, selected = index == 0)
            }
        }
    }
}
```
(`Box`/`weight` give the three cards equal width in a row, matching the mockup. Add imports `androidx.compose.foundation.layout.Box` and keep existing `Row`.)

- [ ] **Step 3: Swap multi-value `Checkbox` → `ValueChip`, wrap groups in `FieldGroup`**

Replace the private `MultiField` composable:
```kotlin
@Composable
private fun MultiField(
    field: String,
    values: List<String>,
    item: ReviewItem,
    store: MergeReviewStore,
) {
    if (values.isEmpty()) return
    Text("$field (keep any)", Modifier.padding(top = 4.dp))
    values.forEach { value ->
        val ev = ExcludedValue(field, value)
        val included = ev !in item.excludedValues
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = included, onCheckedChange = { store.toggleField(item.id, ev) })
            Text(value)
        }
    }
}
```
with a `FieldGroup` + flowing `ValueChip`s (tag = `"field:value"`):
```kotlin
@Composable
private fun MultiField(
    field: String,
    values: List<String>,
    item: ReviewItem,
    store: MergeReviewStore,
) {
    if (values.isEmpty()) return
    com.robsartin.contactotomy.ui.components.FieldGroup("$field (keep any)") {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { value ->
                val ev = ExcludedValue(field, value)
                val included = ev !in item.excludedValues
                com.robsartin.contactotomy.ui.components.ValueChip(
                    label = value,
                    selected = included,
                    onToggle = { store.toggleField(item.id, ev) },
                    tag = "$field:$value",
                )
            }
        }
    }
}
```
Add `import androidx.compose.foundation.layout.FlowRow` and `@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)` on `MergeDetailContent` (FlowRow is experimental in this Compose version). Remove the now-unused `Checkbox` import if no other use remains in the file (the manual-merge picker still uses `Checkbox`, so keep the import).

Wrap the name radio group and the conflict (title/notes) radios in `FieldGroup`s too for consistent headers/spacing — keep their existing label strings and `RadioButton` controls and `chooseName`/`chooseConflict` calls unchanged. The Company/org control (`CompanyOrgField`) stays radios; optionally wrap its header in the same style (keep the exact "Company / org (pick one)" text).

- [ ] **Step 4: Run merge tests; fix only selectors broken by the chip swap / dropped `[tag]`**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.Merge*" --tests "com.robsartin.contactotomy.ui.AppFlowTest" --tests "com.robsartin.contactotomy.ui.AppNavigationTest"`

Fix failures using these rules (do NOT weaken assertions):
- A test that clicked a phone/email/label **Checkbox** (e.g. via `onNodeWithText("+15125551234")` then asserting a toggle, or a checkbox role) now toggles the chip: `onNodeWithTag("phones:+15125551234").performClick()` (tag format `"$field:$value"`). The value text is still displayed, so pure `assertIsDisplayed`/text-presence assertions need no change.
- A test that asserted a cluster-row label containing `"[HIGH]"`, `"[maybe]"`, or `"[manual]"` should assert the bare label (e.g. `onNodeWithText("manual", substring = true)` still works because `ConfidenceChip` renders "manual"). Update `"[manual]"`/`"[HIGH]"` substrings to `"manual"`/`"HIGH"`.
- Everything else (button text "Accept merge"/"Next"/"+ Manual merge"/"Create merge", "Needs review", "Company / org", radio labels) is unchanged — those selectors must still pass untouched.

Iterate until all listed test classes pass.

- [ ] **Step 5: Run full suite + spotless**

Run: `./gradlew test` then `./gradlew spotlessCheck` (apply + re-run if needed).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/MergeScreen.kt src/test/kotlin/com/robsartin/contactotomy/ui
git commit -m "feat(ui): restyle merge screen with themed cards/chips; chips for multi-value (#30)"
```

---

### Task 6: Restyle `ImportScreen` + `ExportScreen`

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/ImportScreen.kt`
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/ExportScreen.kt`

Keep all button/label TEXT and store calls identical (tests assert on "Choose Apple export", "Save cleaned vCard…", counts, etc.).

- [ ] **Step 1: ImportScreen — add a `SectionHeader`, keep rows**

At the top of the `Column` in `ImportScreen`, add:
```kotlin
        com.robsartin.contactotomy.ui.components.SectionHeader("Import your vCard exports")
```
(before the three `PickerRow`s). Leave the `PickerRow`s, importing/error text, count text, and per-file rows otherwise unchanged. This is the minimal, safe polish for Import (its controls are already buttons).

- [ ] **Step 2: ExportScreen — `SectionHeader` + instructions in a card**

In `ExportScreen`, replace:
```kotlin
        Text("Ready to export ${state.contactCount} cleaned contacts")
```
with:
```kotlin
        com.robsartin.contactotomy.ui.components.SectionHeader("Export your cleaned contacts")
        Text("Ready to export ${state.contactCount} cleaned contacts")
```
and wrap the instructions block in a `Card`:
```kotlin
        Text("How to import your cleaned contacts", Modifier.padding(top = 8.dp))
        androidx.compose.material.Card(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(com.robsartin.contactotomy.ui.theme.Dimens.cardRadius),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(10.dp)) {
                Text(instructions)
            }
        }
```
(Replace the existing instructions `Column`. Keep the "Save cleaned vCard…" button, exported/error text, and the heading text exactly.)

- [ ] **Step 3: Run import/export tests + full suite**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.ImportScreenTest" --tests "com.robsartin.contactotomy.ui.ExportScreenTest" --tests "com.robsartin.contactotomy.ui.ExportInstructionsTest"`
Then `./gradlew test`.
Expected: PASS (text selectors unchanged).

- [ ] **Step 4: Spotless + commit**

Run `./gradlew spotlessCheck` (apply if needed), then:
```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/ImportScreen.kt src/main/kotlin/com/robsartin/contactotomy/ui/ExportScreen.kt
git commit -m "feat(ui): section headers + carded instructions on import/export (#30)"
```

---

### Task 7: Restyle `DeletionScreen`

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionScreen.kt`
- Modify (if needed): `DeletionScreenTest.kt`, `DeletionScreenDetailTest.kt`

Keep store calls and button/label TEXT identical ("Rules", "Run", "Approve all", "Load…", "Save…", rule names, the "N flagged · M approved" summary, flagged-row text). Deletion's `Checkbox`es (rule enable, approve contact) STAY as `Checkbox` (not multi-value merge fields). Polish = section headers, card-framed detail, primary-styled Run.

- [ ] **Step 1: Add a `SectionHeader` to each column and card the detail**

- Above the rules list, add `com.robsartin.contactotomy.ui.components.SectionHeader("Rules")` and remove the bare `Text("Rules")` (same text, styled). 
- In the right detail column, wrap `CardDetail(selected)` in a `Card`:
```kotlin
                    androidx.compose.material.Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(com.robsartin.contactotomy.ui.theme.Dimens.cardRadius),
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.padding(10.dp)) { CardDetail(selected) }
                    }
```
- Leave the rule checkboxes, flagged list, approve checkboxes, and summary text unchanged.

- [ ] **Step 2: Run deletion tests + full suite**

Run: `./gradlew test --tests "com.robsartin.contactotomy.ui.Deletion*"` then `./gradlew test`.
Expected: PASS. If a test matched `Text("Rules")` specifically it still passes (SectionHeader renders the same "Rules" text). Fix only genuine breakages without weakening assertions.

- [ ] **Step 3: Spotless + commit**

Run `./gradlew spotlessCheck` (apply if needed), then:
```bash
git add src/main/kotlin/com/robsartin/contactotomy/ui/DeletionScreen.kt src/test/kotlin/com/robsartin/contactotomy/ui
git commit -m "feat(ui): section headers + carded detail on deletion screen (#30)"
```

---

### Task 8: Full gate, run-the-app boot check, push, PR

**Files:** none (verification + PR).

- [ ] **Step 1: Full gate**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL — tests, Kover (line ≥90 / branch ≥70), Spotless, Konsist (`core` UI-free; new code under `ui.theme`/`ui.components`).
If a trivial theme/component object dents branch coverage below the floor, add focused component tests (preferred) — do NOT lower the floor.

- [ ] **Step 2: Boot check (compile + launch smoke)**

Run: `timeout 60 ./gradlew run` (or the project's run task) and confirm it builds and the window starts without exception, then stop it. (Visual correctness is the user's call — see Step 4.)

- [ ] **Step 3: Push + open PR**

```bash
git push -u origin 30-visual-polish
gh pr create --base main --head 30-visual-polish \
  --title "Visual polish: theme + reusable components across all screens (Closes #30)" \
  --body "Implements #30 per docs/superpowers/specs/2026-06-27-visual-polish-design.md. Shared ContactotomyTheme + AppColors/Dimens; reusable SourceBadge/ConfidenceChip/SourceCard/ClusterRow/LabeledProgress/FieldGroup/ValueChip; applied to Merge, Import, Deletion, Export. Presentation only — stores/intents/core unchanged. Multi-value merge fields are now FilterChip toggles (testTags keep selectors valid). Closes #30."
```

- [ ] **Step 4: Watch CI + hand off for visual sign-off**

Run: `gh pr checks <PR#> --watch`. On green, tell the user it needs their run-the-app visual review (a screenshot in the PR) — green tests prove behavior, not the look.

---

## Self-Review

**Spec coverage:**
- §3 theme (AppColors palette, Dimens, ContactotomyTheme, wrap App) → Task 1. ✓
- §4 components (SourceBadge, ConfidenceChip, SourceCard, ClusterRow, LabeledProgress, FieldGroup, SectionHeader, ValueChip) → Tasks 2–4. ✓
- §5 per-screen application (Merge centerpiece; Import/Export/Deletion) → Tasks 5–7. ✓
- §2/§6 multi-value → FilterChip with testTags; tests updated; single-value stay radios → Task 5. ✓
- §6 component tests + updated UI tests + gate + run-the-app → Tasks 2–4, 5–7, 8. ✓
- §2 no behavior change / core untouched → enforced per task (only `ui` files; store calls preserved). ✓

**Placeholder scan:** none — every code step has full code; test-update step gives concrete selector-rewrite rules + tag format rather than vague "fix tests".

**Type consistency:** `appColors`/`AppColors` fields used consistently; `ValueChip(label, selected, onToggle, tag, fontSize=)` matches its call site in `MultiField` (`tag = "$field:$value"`) and its test; `ClusterRow(title, origin, selected, onClick)` and `SourceCard(contact, primary, selected)` match their call sites in MergeScreen and their tests; `displayName(ContactName)` is the shared `ui/ContactDisplay.kt` function; `FieldGroup(header){content}` and `LabeledProgress(reviewed,total)` consistent across uses.

**Note on scope:** Task 5 is the largest (the centerpiece + test updates). If an implementer reports it as too large, it can be split into 5a (left list + source cards) and 5b (multi-value chips + test fixes) along the existing step boundaries.

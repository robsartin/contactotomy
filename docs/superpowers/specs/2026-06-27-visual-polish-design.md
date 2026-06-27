# Visual Polish — Design Spec (#30)

Date: 2026-06-27
Status: Approved (design); pending implementation plan
Builds on: all functional screens (Import/Merge/Deletion/Export, manual merge,
name+company). Presentation only — no behavior change. core engine untouched.
Tracking issue: #30
Reference mockups (ephemeral, gitignored): `.superpowers/brainstorm/58297-1782509031/content/merge-redesign.html` (merge centerpiece), `.superpowers/brainstorm/47103-1782490076/content/deletion-layout.html`.

## 1. Purpose

The Compose UI is functional but plain (default Material buttons, plain `Text`
rows) compared to the approved mockups. Bring it up to that fidelity: a small
shared theme, reusable styled components, bordered cards, colored source badges,
confidence chips, a progress bar, and clear hierarchy — across all four screens,
in one pass. **Presentation only**: intents, stores, and `core` are unchanged, and
existing test selectors stay valid (text/labels stable; `testTag`s added where a
control type changes).

## 2. Decisions

- **One PR, all four screens** (Merge, Import, Deletion, Export) share a single
  theme so the end state is consistent.
- **Theme palette = the approved mockup** (not generic Material): accent
  `#4a90d9`, success/accept `#2e7d32`, error/reject `#c62828`, plus domain colors
  (`appleBlue #4a90d9`, `googleGrey #777`, `highChip` `#e7f0ff`/`#185fa5`,
  `maybeChip` `#fff3d6`/`#854f0b`, `star #e8a000`).
- **Multi-value fields (phones/emails/labels/urls/addresses) → `FilterChip`
  toggles** (the "fancier" look). Single-value fields (name, Company/org,
  title/notes conflicts) **stay `RadioButton`s**.
- **No behavior change.** Store intents (`toggleField`, `chooseConflict`,
  `chooseName`, `chooseOrg`, `accept`/`reject`/`undo`, etc.) are called exactly as
  today. Where a control type changes (Checkbox→FilterChip), add
  `Modifier.testTag(...)` and update the affected UI tests to select by tag/label.
- **core untouched; new code is `ui` only** (theme + components). Konsist still
  forbids `core` importing compose/ui.

## 3. Theme (`ui/theme/`)

- `Theme.kt` — `@Composable fun ContactotomyTheme(content)` wrapping
  `MaterialTheme(colors = lightColors(primary = accent, ...))` and providing an
  `AppColors` via `staticCompositionLocalOf` (the domain colors Material's `Colors`
  doesn't model). `App` wraps its root in `ContactotomyTheme`.
- `AppColors.kt` — `data class AppColors(appleBlue, googleGrey, highChipBg,
  highChipFg, maybeChipBg, maybeChipFg, cardBg, cardBorder, selectedBorder, star,
  mergedBorder, ...)` + `val LocalAppColors = staticCompositionLocalOf { ... }` and
  a convenience `val appColors @Composable get() = LocalAppColors.current`.
- `Dimens.kt` — paddings (`xs=4, sm=8, md=12, lg=16`), radii (`card=10, chip=12`),
  border widths (`hairline=1, selected=2`) as `Dp` constants.
- `Type.kt` — a small `Typography` (sectionHeader = 10sp uppercase muted; body;
  cardTitle 500) — reuse Material `Typography` slots where possible; only add what
  the mockup needs.

Colors are app-fixed (the desktop window is light); no dark-mode requirement for
the app itself (YAGNI).

## 4. Reusable components (`ui/components/`)

Each is a small, focused composable with a clear interface, independently testable:

- `SourceBadge(source: Source)` — pill: Apple→blue, Google→grey, FILE→grey, with
  label text.
- `ConfidenceChip(origin: Origin)` — pill: HIGH (`highChip`), UNCERTAIN→"maybe"
  (`maybeChip`), MANUAL→"manual".
- `SourceCard(contact, primary: Boolean, selected: Boolean)` — bordered `Card`:
  header row (SourceBadge, ★ when primary, date), name, phones/emails/org lines.
- `ClusterRow(item, selected, onClick)` — bordered card row; selected = 2px accent
  border; trailing ConfidenceChip; uncertain → amber card bg.
- `LabeledProgress(reviewed: Int, total: Int)` — `LinearProgressIndicator` (themed)
  + "x of y reviewed".
- `FieldGroup(header: String, content)` — uppercase muted header + spaced content
  column.
- `ValueChip(label: String, selected: Boolean, onToggle: () -> Unit, testTag)` —
  a `FilterChip` (`@OptIn(ExperimentalMaterialApi::class)`) used for multi-value
  include/exclude.
- `SectionHeader(text)`, `PrimaryButton(...)`, `DangerButton(...)` — thin styled
  wrappers over Material `Button`/`Text`.

Components read theme via `MaterialTheme`/`appColors`; they hold no state and call
back through lambdas.

## 5. Per-screen application (presentation only)

- **MergeScreen:** left list → `ClusterRow`s under "Needs review (N)" + `Accept all
  high-conf` (PrimaryButton) + `LabeledProgress`; Resolved section visually
  de-emphasized with Undo. Right → `SourceCard`s; merged-result in a
  `mergedBorder` card; name + Company/org + title/notes via `RadioButton` inside
  `FieldGroup`s; phones/emails/labels via `ValueChip` (FilterChip) inside
  `FieldGroup`s; pinned footer with `PrimaryButton("✓ Accept merge")` /
  `DangerButton("✕ Keep separate")`. The manual-merge picker keeps its behavior,
  restyled (search field, card rows).
- **ImportScreen:** `SectionHeader`, source-pick buttons styled, imported-count line.
- **DeletionScreen:** rule list + flagged contacts as cards (`SourceCard`/section
  cards), Run as `PrimaryButton`, Approve/Skip styled, per the deletion mockup.
- **ExportScreen:** `SectionHeader`, summary count, `PrimaryButton` export,
  instructions in a styled card.

## 6. Testing

- **Component tests** (`runComposeUiTest`): `SourceBadge` shows "Apple"/"Google"
  with the right color (assert via testTag + displayed text); `ConfidenceChip`
  renders "HIGH"/"maybe"/"manual" for each origin; `ValueChip` toggle invokes its
  `onToggle`; `LabeledProgress` shows "x of y reviewed".
- **Existing UI tests updated** for the Checkbox→FilterChip swap on
  phones/emails/labels: where a test clicked a `Checkbox`, it now clicks the
  `ValueChip` by `testTag` (e.g. `phones:+15125551234`) or its label text. Keep all
  other selectors (button/section text) unchanged. Affected: `MergeDetailTest`,
  `MergeScreenListTest`, `MergeScreenManualMergeTest`, `MergeScreenCompanyTest`,
  `AppFlowTest`, deletion/export screen tests — update only what the swap breaks.
- `./gradlew check` green (line ≥90 / branch ≥70); Spotless; Konsist (`core`
  UI-free; new code under `ui.theme`/`ui.components`).
- **Run-the-app screenshot in the PR** — the user's visual sign-off is the gate;
  green tests do not prove the look.

## 7. Scope

In scope: `ui/theme/*`, `ui/components/*`, restyling the four screen composables
and the manual-merge picker, their tests. Out of scope: any store/intent/`core`
change; new features; dark mode; the matcher-linking Phase 2 of #29.

## 8. Risks / notes

- `FilterChip` is `ExperimentalMaterialApi` in Compose Material 1.7.3 — used with a
  localized `@OptIn`.
- The chip swap is the only behavior-adjacent change; `testTag`s + test updates
  contain it. If a Kover/Konsist exclusion is needed for a trivial theme object, it
  is added explicitly and noted (no floor lowering).
- Components are kept small and focused so each fits in context and is reliably
  editable; `MergeScreen.kt` shrinks as row/card/field rendering moves into
  components.

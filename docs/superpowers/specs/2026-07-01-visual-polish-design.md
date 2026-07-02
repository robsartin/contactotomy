# Visual polish pass across all screens — Design Spec (#84)

Date: 2026-07-01
Status: Approved (autonomous)
Tracking issue: #84

## 1. Purpose

Make Contactotomy feel like one polished app rather than plain Material defaults:
a consistent component kit, a real color theme with **dark mode**, better layout/
density with **visible scrollbars everywhere**, and polish moments (nicer step
indicator, empty states, icons/spacing). Aesthetic north-star: the earlier HTML
mockups (issue #30).

## 2. North-star mockups (read these first)

Reference these committed mockups for palette, spacing, card styling, and layout cues
(extract the look; do NOT try to match pixel-for-pixel or restructure working screens):
- `.superpowers/brainstorm/58297-1782509031/content/merge-redesign.html`
- `.superpowers/brainstorm/14386-1782351309/content/merge-layout.html`
- `.superpowers/brainstorm/14386-1782351309/content/merge-detail.html`
- `.superpowers/brainstorm/47103-1782490076/content/deletion-layout.html`

## 3. HARD CONSTRAINT — do not break the UI test suite

There are 500+ tests, many Compose UI tests keyed on `testTag`s and visible text.
**Preserve every existing `testTag` and visible text string.** This pass is
**stylistic** (colors, typography, spacing, component wrappers, dark mode, scrollbars,
step indicator, empty-state text that is NEW where none existed) — do NOT rename tags,
change assertion-relevant button/label text, or restructure node trees in ways that
break existing tests. Run the full suite after every task; if a test breaks from a
styling change, prefer adjusting the style over changing the test (only touch a test
if the change is unavoidable and clearly correct, and say so).

## 4. Theme foundation (`ui/theme`)

- **`AppColors`**: keep the current fields (they're already referenced widely). Add a
  **dark** palette: provide `AppColors.light()` (the current values) and
  `AppColors.dark()` (dark-appropriate variants — dark surfaces, lighter text,
  adjusted accent/chip colors drawn from the mockups). Default stays light.
- **`ContactotomyTheme(darkMode: Boolean = false, content)`**: choose
  `AppColors.dark()`/`darkColors(...)` vs `AppColors.light()`/`lightColors(...)`
  accordingly, and pass a **`Typography`** (define a small type scale — h6/subtitle/
  body/caption sizing & weights — instead of Material defaults). Provide the chosen
  `AppColors` via `LocalAppColors`.
- **`Dimens`**: extend as needed (e.g. `xl`, section gaps) — additive only.

## 5. Dark-mode toggle

- `AppState` gains `darkMode: Boolean = false`; `AppStore.toggleDarkMode()`.
- `App.kt` wraps content in `ContactotomyTheme(darkMode = state.darkMode)` and adds a
  compact toggle control in the top bar (testTag `dark-mode-toggle`; a ☾/☀ icon or a
  small labeled switch). Session-only (no persistence — that's the deferred settings
  work in #69).

## 6. Component kit (`ui/components`)

Polish the shared components so screens compose from them consistently:
- `Cards.kt` / `Badges.kt` / `Fields.kt`: apply theme colors, `Dimens`, rounded
  corners, subtle borders/elevation per the mockups.
- Add (or unify) a **`SectionHeader`** style, a **primary/secondary button** style, and
  a nicer **`StepIndicator`** (currently a bare "▸ label" row in `App.kt`) — a proper
  stepper showing progress (done/current/upcoming) with the accent color. Keep the same
  step labels (Import · Review · Deletion · Export).
- **Empty states**: where a screen/list can be empty, show a friendly styled message
  (add NEW text; don't change existing assertion text).

## 7. Apply across screens

Adopt the theme + components consistently in `ImportScreen`, `ReviewScreen`
(+`MergeScreen`), `DeletionScreen`, `ExportScreen`, `RuleBuilderDialog`:
- consistent spacing/density and section headers,
- theme colors instead of ad-hoc greys,
- **visible scrollbars** (`VerticalScrollbar` + `rememberScrollbarAdapter`) on any
  scrollable area that lacks one (Deletion and Review already have some from #83/#81;
  extend to Import/Export and any remaining lists) — position visible,
- verify readability in BOTH light and dark.

## 8. Testing

- **`AppStore`**: `toggleDarkMode()` flips `darkMode`.
- **Theme**: `ContactotomyTheme(darkMode = true)` provides the dark `AppColors` (a small
  test asserting a dark-palette value is provided via `LocalAppColors`), light by default.
- **`App`/screens UI**: the `dark-mode-toggle` renders and toggling updates state; the
  step indicator renders all four labels; existing screen tests still pass unchanged.
- Add empty-state tests where new empty text is introduced.
- `./gradlew check` green: line ≥90 / branch ≥70 (cover new branches — the toggle,
  the light/dark selection), Spotless/ktlint, Konsist (all `ui`).

## 9. Scope / notes

- Stylistic pass; **no behavioral change** to merge/tidy/deletion/export logic.
- Dark mode is session-only; persisting it is deferred to #69 (settings).
- YAGNI: no theme editor, no multiple themes — one light + one dark.
- If a screen's layout genuinely needs restructuring for the mockup look, keep testTags
  and text stable so tests survive.

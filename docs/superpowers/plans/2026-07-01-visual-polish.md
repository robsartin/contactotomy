# Visual polish pass (#84) — Implementation Plan

> Pure TDD where testable; each task ends `./gradlew check` green + commit.

**Goal:** Consistent component kit + color theme with dark mode + layout/density +
scrollbars everywhere + polish moments, across all screens; north-star = the #30 mockups.

## Global Constraints
- **HARD:** preserve every existing `testTag` and assertion-relevant visible text — this
  is a stylistic pass; run the FULL suite after each task (500+ tests must stay green).
- Coverage floors line ≥90 / branch ≥70 (never lower). All `ui`; Konsist green.
- No behavioral change to merge/deletion/export logic. Spotless/ktlint green.
- Commit messages end `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch `84-visual-polish`. First commit: copy spec →
  `docs/superpowers/specs/2026-07-01-visual-polish-design.md` and this plan →
  `docs/superpowers/plans/2026-07-01-visual-polish.md`. Read the spec (esp. §2 mockups,
  §3 the hard test constraint) first, and read the four mockup HTML files.

---

### Task 1: Theme foundation + dark mode
**Files:** `ui/theme/AppColors.kt`, `ui/theme/Theme.kt`, `ui/theme/Dimens.kt`,
`ui/AppState.kt`, `ui/AppStore.kt`, `ui/App.kt`; tests `ui/AppStore*Test.kt`, a theme test.

- [ ] **Step 1: Failing tests** — `AppStore.toggleDarkMode()` flips `AppState.darkMode`
  (default false); `ContactotomyTheme(darkMode = true)` provides a dark `AppColors`
  (assert a dark-palette value via `LocalAppColors` in a composable test); default is light.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Add `AppColors.light()`/`AppColors.dark()` (dark values from the
  mockups), a `Typography`, `ContactotomyTheme(darkMode = false, content)` selecting
  light/dark colors; `AppState.darkMode` + `AppStore.toggleDarkMode()`; `App.kt` wraps in
  `ContactotomyTheme(darkMode = state.darkMode)` and adds a `dark-mode-toggle` control in
  the top bar.
- [ ] **Step 4:** Run, confirm pass; run FULL suite (existing screen tests must stay green).
- [ ] **Step 5:** Commit.

### Task 2: Component kit + step indicator + empty states
**Files:** `ui/components/Cards.kt`, `Badges.kt`, `Fields.kt`, a new/updated
`SectionHeader`/button/StepIndicator (extract `StepIndicator` from `App.kt` into a styled
component); tests `ui/components/*Test.kt`.

- [ ] **Step 1: Failing/So tests** — the styled `StepIndicator` renders all four labels
  (Import/Review/Deletion/Export) and marks the current one; component tests for any new
  styled component render with expected testTags/text. (Keep existing component tests green.)
- [ ] **Step 2:** Run, confirm fail where new.
- [ ] **Step 3:** Polish the shared components with theme colors/`Dimens`/rounded corners
  per the mockups; upgrade the step indicator to a proper stepper (same labels); add
  styled empty-state text where lists can be empty (NEW text only). Preserve all tags/text.
- [ ] **Step 4:** Run, confirm pass; FULL suite green.
- [ ] **Step 5:** Commit.

### Task 3: Apply across screens + scrollbars + gate
**Files:** `ui/ImportScreen.kt`, `ui/ReviewScreen.kt`, `ui/MergeScreen.kt`,
`ui/DeletionScreen.kt`, `ui/ExportScreen.kt`, `ui/RuleBuilderDialog.kt`; tests as needed.

- [ ] **Step 1:** Adopt the theme colors + polished components + consistent spacing across
  these screens; add `VerticalScrollbar` + `rememberScrollbarAdapter` to any scrollable
  area lacking one (Import/Export and any remaining lists). Verify readability in light
  AND dark (visually reason via the color values). Preserve every testTag/text.
- [ ] **Step 2:** Run the FULL suite; fix styling until green (adjust styles, not tests,
  unless a change is unavoidable and clearly correct).
- [ ] **Step 3:** `./gradlew check` fully green (coverage floors held; Konsist/Spotless).
- [ ] **Step 4:** Commit. Open PR `Closes #84`.

## Self-review
- Dark mode toggles; light default; theme + components applied app-wide; scrollbars
  everywhere; step indicator upgraded. All existing testTags/text preserved; full suite
  green; coverage floors held. No behavioral change.

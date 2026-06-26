# Deletion Review (Plan 4c) — Design Spec

Date: 2026-06-25
Status: Approved (design); pending implementation plan
Builds on: Plan 3 rules engine (ADR-0007/0011), Plan 4a (`AppStore`, `FilePicker`), Plan 4b-1 (`MergeReviewStore` pattern), ADR-0012 (UI testing), ADR-0013 (per-screen store).
Tracking issue: #16
Part of: Plan 4 (Compose UI), sub-plan 4c of 4 (4d = export + instructions remains).

## 1. Purpose

The deletion-review screen: over the post-merge contacts, the user toggles which
saved rules are active, runs them, reviews the flagged cards (each with the reason
it was flagged), approves which to actually delete, and applies — producing the
final contact set for export. Nothing is deleted without explicit approval
(ADR-0007).

Chosen UX (visual brainstorming): **layout A — three panes** (Rules | Flagged |
Card).

## 2. Decisions

- **Rule management = toggle + run saved rules.** No in-app condition editing;
  complex/custom rules are hand-edited in the JSON file or brought in via Load.
- **Explicit Load/Save** of the rules JSON (file picker); the screen seeds from
  `RuleSet.starter()`.
- **`run()` clears prior approvals** — you re-approve against fresh results.
- **Load/Save I/O lives in the composable**; the store takes/returns JSON strings
  so it is filesystem-free and unit-testable.
- **Default selection: none** — the Card pane prompts to select a flagged row.
- Logic in a testable **`DeletionReviewStore`**, following the per-screen-store
  pattern established for the merge screen (ADR-0013); no new ADR needed.

## 3. Data flow

On entering Deletion, 4c operates on `AppStore.mergedContacts ?: AppStore.contacts`
(the post-merge set, ADR-0007). It uses the built `core.rules` engine:
`RuleEngine.evaluate(contacts, ruleSet)` → `List<Flagged>` (contact + per-rule
reasons), `RuleStore` for JSON (de)serialization, and `applyDeletions(contacts,
approvedIds)` for the final set. On commit the final `List<Contact>` is written to
`AppStore` as `finalContacts`, which the Export step (4d) consumes. The engine is
already built and tested in `core`; 4c is store + UI.

## 4. Review store

Package `com.robsartin.contactotomy.ui` (depends on `core`; Konsist keeps `core`
UI-free).

```kotlin
data class RuleToggle(val rule: Rule, val enabled: Boolean = true)

data class DeletionReviewState(
    val rules: List<RuleToggle>,
    val flagged: List<Flagged> = emptyList(),   // core.rules.Flagged
    val approvedIds: Set<String> = emptySet(),
    val hasRun: Boolean = false,
    val committed: Boolean = false,
)
```

`DeletionReviewStore(contacts, initialRules: RuleSet = RuleSet.starter())` exposes
`StateFlow<DeletionReviewState>` and intents:

- `toggleRule(ruleName)` — flip a rule's `enabled` (does not auto-run).
- `run()` — `RuleEngine.evaluate(contacts, RuleSet(enabledRules))` → `flagged`;
  `hasRun = true`; **clears `approvedIds`**.
- `approve(id)` / `unapprove(id)`; `approveAllForRule(ruleName)` (approve every
  flagged contact whose matches include that rule); `approveAll()` /
  `clearApprovals()`.
- `loadRules(json)` — `RuleStore.fromJson` → replace `rules` (all enabled), clear
  `flagged`/`approvedIds`. `rulesToJson(): String` — `RuleStore.toJson` of the
  current rules (enabled and disabled) for saving.
- `commit()` — `applyDeletions(contacts, approvedIds)` → final list;
  `committed = true`.

**Approval guard:** `approvedIds` only ever holds ids currently in `flagged`;
approving an id that is not flagged is a no-op, so a stale approval can never
delete an unflagged contact. (Both `approve` and `run`'s reset uphold this.)

Pure and deterministic for unit tests; the composable does the file I/O.

## 5. UI (three panes)

**Left — Rules:** checklist of `rules` (name + enabled checkbox → `toggleRule`);
**Load…** / **Save…** buttons (open `FilePicker`; Load reads file → `loadRules`,
Save writes `rulesToJson()` to the chosen path); **Run** (→ `run()`); a hint when
no rule is enabled.

**Middle — Flagged**, grouped by matched rule: each group header
"`*@indeed.com` (118)" with an **Approve all** action (`approveAllForRule`); under
it, each flagged contact as a row with an approve checkbox, name, and reason. A
top-level **Approve all** / **Clear**, and an empty state ("Run rules to see
matches"). A contact matched by several rules appears under each group but is one
id in `approvedIds`.

**Right — Card:** the selected flagged contact's full card (name, phones, emails,
org, source, dates) and the reason(s) it was flagged. Default: nothing selected
("Select a flagged contact").

**Bottom bar:** summary "N flagged · M approved → K contacts remain" and **Apply
deletions & continue** → `commit()` → writes `finalContacts` to `AppStore` →
advances to Export.

Composables stay thin over the store; covered by `runComposeUiTest`. The native
Load/Save dialogs are the only excluded glue — a SAVE-mode variant is added
alongside the existing LOAD `FilePicker`/`AwtFilePicker`.

## 6. Testing

- **`DeletionReviewStore` unit tests** (no Compose): toggleRule; run() flags via
  enabled rules only and clears approvals; approve/unapprove; approveAllForRule
  approves exactly the rule's matches; approveAll/clearApprovals; approval guard
  (non-flagged id is a no-op); loadRules(json) replaces rules + clears
  flagged/approvals; rulesToJson() round-trips via RuleStore; commit() removes
  exactly the approved ids.
- **Compose UI tests** (`runComposeUiTest`, fake `FilePicker`): rules toggle; Run
  fills flagged groups with counts; approve-all-for-rule checks its rows;
  selecting a row shows card + reason; summary updates; Apply commits + advances.
- Konsist keeps `core` UI-free; coverage floors line ≥90 / branch ≥65.

## 7. Scope

In scope: `DeletionReviewStore`, the three-pane Deletion screen, a SAVE-mode
`FilePicker` variant, `AppStore.finalContacts`, wiring into the wizard. Out of
scope: in-app rule/condition editing, the Export screen (4d), usage signals.
Single coherent sub-plan (no further decomposition).

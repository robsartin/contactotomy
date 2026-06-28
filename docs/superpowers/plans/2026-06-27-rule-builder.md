# In-App Deletion-Rule Builder — Implementation Plan (#66)

> **For agentic workers:** implement task-by-task with pure TDD. Steps use checkbox
> (`- [ ]`) syntax. Each task ends with `./gradlew check` green and a commit.

**Goal:** A recursive, in-app editor to create/edit/delete deletion rules, launched
as a modal from the Deletion screen, with a live match count. No `core` changes.

**Architecture:** A UI-only mutable mirror tree (`BuilderNode`) with stable ids and
pure conversions to/from the immutable `Condition` AST; a `RuleBuilderStore` driving
one rule's edit; a recursive `RuleBuilderDialog`; CRUD on `DeletionReviewStore`;
wiring in `DeletionScreen`.

**Tech Stack:** Kotlin 2.0.21, Compose Desktop 1.7.3 (Material 2), kotlinx-coroutines
`StateFlow`, kotlin.test + Compose UI test.

## Global Constraints

- Pure TDD: failing test first → confirm fail → implement → confirm pass → commit.
- `core` stays untouched and UI-free; ALL new code under `src/main/kotlin/.../ui/`.
- Coverage floors: line ≥90, branch ≥70 (never lower). Cover new branches.
- Spotless/ktlint + Konsist must pass (`./gradlew check`); run `./gradlew spotlessApply` if needed.
- YAGNI: only what this spec lists. Match existing store/screen patterns
  (immutable state + `StateFlow` + intent methods; testTags on interactive nodes).
- Reuse `Condition`, `TextMatch`, `PhoneMatch`, `Predicate`, `And`, `Or`, `Not`,
  `TextField`, `PredicateKind`, `Rule`, `RuleSet`, `RuleEngine`, `RuleStore`,
  `Source` from `com.robsartin.contactotomy.core.*`.
- Commit messages end with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Branch: `66-rule-builder`. First commit: copy this plan + the design spec into
  `docs/superpowers/plans/` and `docs/superpowers/specs/` (filenames
  `2026-06-27-rule-builder.md` and `2026-06-27-rule-builder-design.md`).

---

### Task 1: Mirror-tree model + conversions

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/RuleBuilderModel.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/RuleBuilderModelTest.kt`

**Produces:** `sealed interface BuilderNode { val id: String }` with `LeafText`,
`LeafPhone`, `LeafPredicate`, `BranchAnd`, `BranchOr`, `BranchNot`; `enum NodeKind`;
`fun BuilderNode.toConditionOrNull(): Condition?`;
`fun Condition.toBuilderNode(nextId: () -> String): BuilderNode`;
`fun BuilderNode.replace(id, f)`, `removeChild(id)`, `addChild(parentId, child)`.

- [ ] **Step 1: Failing tests.** Cover: each leaf valid→Condition and invalid→null
  (blank glob/pattern; CREATED_BEFORE w/o date→null; SOURCE_IS w/o source→null);
  `BranchAnd` empty→null, one-invalid-child→null, all-valid→`And`; same for `BranchOr`;
  `BranchNot` empty→null, valid→`Not`; `addChild`/`removeChild`/`replace` mutate the
  node with the given id and leave siblings intact; **round-trip**: for
  `And(of=[Or(of=[TextMatch(EMAIL,"*@x.com"), PhoneMatch("+1*")]), Not(Predicate(NO_EMAIL))])`,
  `c.toBuilderNode(idGen).toConditionOrNull() == c`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement the model + conversions + helpers exactly as in the design
  spec §3 (the spec has the full `toConditionOrNull`/`toBuilderNode` bodies). For the
  pure helpers, recurse over `BranchAnd.children`/`BranchOr.children`/`BranchNot.child`.
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 2: RuleBuilderStore (state, intents, validity)

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/RuleBuilderStore.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/RuleBuilderStoreTest.kt`

**Consumes:** Task 1. **Produces:** `class RuleBuilderStore(contacts, existing: Rule? = null)`
with `state: StateFlow<RuleBuilderState>`, `RuleBuilderState(name, root)`, the intent
methods in design §4, `toRuleOrNull(): Rule?`, `val isValid: Boolean`,
`fun matchCount(): Int?`.

- [ ] **Step 1: Failing tests.** New store: root is an empty `LeafText`, `isValid`
  false, `matchCount()` null. `setGlob(rootId, "*@x.com")` → `isValid` true,
  `toRuleOrNull()` == `Rule("", TextMatch(EMAIL,"*@x.com"))` only once name set; pin
  that blank name ⇒ invalid. `setName`. `changeKind(rootId, AND)` then
  `addChild(rootId)` adds a default `LeafText` child. `setField`/`setPattern`/
  `setPredicateKind`/`setBefore`/`setSource` mutate the right node by id. `removeNode`
  on a child removes it; on root is a no-op. `matchCount` against a fixture: build a
  rule flagging a known subset of `contacts` and assert the count. Edit construction:
  `RuleBuilderStore(contacts, existing = Rule("r", And(of=[...])))` yields a root
  whose `toConditionOrNull()` equals that condition and name "r".
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement. Use an internal `Int` counter for ids (e.g. `"n0"`,`"n1"`…),
  seeded so edit-construction ids are unique. `matchCount()` = `toRuleOrNull()?.let {
  RuleEngine.evaluate(contacts, RuleSet(listOf(it))).size }`. Document `changeKind`
  rules (AND↔OR keep children; →NOT keeps first child; branch→leaf drops children;
  leaf→branch starts empty).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 3: DeletionReviewStore CRUD

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionReviewStore.kt`
- Test: existing `DeletionReviewStoreTest.kt` (add cases)

**Produces:** `fun addRule(rule: Rule)`, `fun updateRule(originalName: String, rule: Rule)`,
`fun removeRule(name: String)`.

- [ ] **Step 1: Failing tests.** `addRule` appends a toggle with `enabled = true`;
  the new rule participates in `run()`. `updateRule("old", Rule("new", cond))` replaces
  the toggle whose `rule.name == "old"`, preserving its `enabled` flag and list
  position. `removeRule("x")` drops it. `rulesToJson()` round-trips an added rule via
  `RuleStore.fromJson`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement with `_state.update`, mirroring existing `toggleRule`.
  (Find the toggle type holding `rule` + `enabled`; reuse it.)
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 4: Recursive RuleBuilderDialog

**Files:**
- Create: `src/main/kotlin/com/robsartin/contactotomy/ui/RuleBuilderDialog.kt`
- Test: `src/test/kotlin/com/robsartin/contactotomy/ui/RuleBuilderDialogTest.kt`

**Consumes:** Tasks 1–2. **Produces:** `@Composable fun RuleBuilderDialog(store:
RuleBuilderStore, onSave: (Rule) -> Unit, onCancel: () -> Unit)`.

- [ ] **Step 1: Failing UI tests** (Compose, `runComposeUiTest`, drive by testTag):
  with a fresh store, `save-rule` is disabled; typing into `rule-name` and `glob:<rootId>`
  enables it; `match-count` text is present; clicking `save-rule` invokes `onSave` with
  the built `Rule`. For a branch root (construct store from an existing `And`), an
  `add-child:<id>` button adds a child editor and `delete:<id>` removes one. Use stable
  testTags: `rule-name`, `node-kind:<id>`, `field:<id>`, `glob:<id>`, `pattern:<id>`,
  `predicate-kind:<id>`, `add-child:<id>`, `delete:<id>`, `match-count`, `save-rule`,
  `cancel-rule`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement the recursive `NodeEditor(node, store)` per design §5,
  reading `store.state` and calling intents; leaf vs branch vs not rendering; Save
  disabled unless `store.isValid`; Save calls `onSave(store.toRuleOrNull()!!)`.
  Follow existing component/theme style (`Dimens`, Material 2). Keep `NodeEditor`
  small; recurse into children with indentation.
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 5: DeletionScreen wiring

**Files:**
- Modify: `src/main/kotlin/com/robsartin/contactotomy/ui/DeletionScreen.kt`
- Test: existing `DeletionScreenTest.kt` (add cases)

**Consumes:** Tasks 2–4.

- [ ] **Step 1: Failing UI tests.** A "New rule…" button (testTag `new-rule`) opens
  the dialog; building + saving a rule makes it appear in the left rules list and it
  flags contacts on `Run`. A per-rule "Edit" (`edit-rule:<name>`) opens the dialog
  populated; "Delete" (`delete-rule:<name>`) removes the rule from the list.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Implement: hold `var editing by remember { mutableStateOf<Rule?>(null) }`
  and `var showNew by remember { mutableStateOf(false) }`; render `RuleBuilderDialog`
  when open, constructing a `RuleBuilderStore(contacts, existing)`; on save call
  `store.addRule`/`store.updateRule` and close; add Edit/Delete controls per rule row.
  The screen needs the contacts for the builder store — pass them from
  `DeletionReviewStore` (add a read-only `contacts` accessor if not already exposed).
- [ ] **Step 4:** Run, confirm pass.
- [ ] **Step 5:** Commit.

### Task 6: End-to-end + gate

**Files:**
- Modify: `src/test/kotlin/com/robsartin/contactotomy/ui/AppFlowTest.kt` (one new flow)

- [ ] **Step 1: Failing e2e test.** Import a fixture, advance to Deletion, click
  `new-rule`, build a rule that flags a known card (e.g. `TextMatch(EMAIL, "*@spam.com")`),
  Save, `Run`, `Approve all`, Next → Export; assert the flagged card is gone from
  `finalContacts`. Reuse an existing fixture or add a small `.vcf`.
- [ ] **Step 2:** Run, confirm fail.
- [ ] **Step 3:** Make it pass (wiring should already support it; adjust testTags if needed).
- [ ] **Step 4:** `./gradlew check` fully green.
- [ ] **Step 5:** Commit. Open PR `Closes #66`.

## Self-review checklist (run before PR)
- Spec coverage: model+conversions (T1), store (T2), CRUD (T3), dialog (T4), screen (T5), e2e (T6). ✓
- No placeholders; every interactive node has a stable testTag.
- Types consistent: `BuilderNode`/`NodeKind`/`toConditionOrNull`/`toBuilderNode`
  names identical across tasks.
- `core` untouched; coverage floors held.

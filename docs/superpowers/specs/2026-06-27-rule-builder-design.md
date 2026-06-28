# In-App Deletion-Rule Builder — Design Spec (#66)

Date: 2026-06-27
Status: Approved (design)
Tracking issue: #66

## 1. Purpose

Let the user create, edit, and delete deletion rules inside the app — a recursive
condition editor — instead of hand-writing `rules/*.json`. Reuses the existing
`core/rules` engine and `RuleStore` unchanged; **all new code is in the `ui` layer.**

## 2. Decisions

- **Full recursive composer.** The editor supports arbitrary `And`/`Or`/`Not`
  nesting to any depth, plus the three leaf conditions (`TextMatch`, `PhoneMatch`,
  `Predicate`).
- **Modal dialog**, launched from the Deletion screen via a "New rule…" button and
  per-rule "Edit" buttons. Closes back to the Deletion screen.
- **Create, edit & delete** on the in-session active rule set. Edit loads an
  existing rule's condition back into the editor. JSON Save…/Load… (existing) then
  persist/replace as today.
- **Live match count** while editing: the in-progress condition is evaluated
  against the loaded contacts via `RuleEngine`, shown as "matches N of M".
- **No `core` changes.** `Condition`, `RuleEngine`, `RuleStore`, `Rule`, `RuleSet`
  are reused as-is.

## 3. The editable mirror tree (key technical choice)

The immutable `Condition` AST is awkward to edit (no node identity; nowhere to hold
an incomplete/invalid node mid-edit). Introduce a UI-only mutable mirror tree with
stable ids, and pure conversions to/from `Condition`.

`ui/RuleBuilderModel.kt`:

```kotlin
sealed interface BuilderNode { val id: String }

data class LeafText(override val id: String, val field: TextField = TextField.EMAIL, val glob: String = "") : BuilderNode
data class LeafPhone(override val id: String, val pattern: String = "") : BuilderNode
data class LeafPredicate(
    override val id: String,
    val kind: PredicateKind = PredicateKind.NO_EMAIL,
    val before: java.time.Instant? = null,
    val source: Source? = null,
) : BuilderNode
data class BranchAnd(override val id: String, val children: List<BuilderNode> = emptyList()) : BuilderNode
data class BranchOr(override val id: String, val children: List<BuilderNode> = emptyList()) : BuilderNode
data class BranchNot(override val id: String, val child: BuilderNode? = null) : BuilderNode
```

Pure conversions (in the same file):

```kotlin
// null when the (sub)tree is not yet a valid Condition
fun BuilderNode.toConditionOrNull(): Condition? = when (this) {
    is LeafText -> glob.takeIf { it.isNotBlank() }?.let { TextMatch(field, it) }
    is LeafPhone -> pattern.takeIf { it.isNotBlank() }?.let { PhoneMatch(it) }
    is LeafPredicate -> when (kind) {
        PredicateKind.CREATED_BEFORE -> before?.let { Predicate(kind, before = it) }
        PredicateKind.SOURCE_IS -> source?.let { Predicate(kind, source = it) }
        else -> Predicate(kind)
    }
    is BranchAnd -> children.map { it.toConditionOrNull() }
        .takeIf { it.isNotEmpty() && it.all { c -> c != null } }
        ?.let { And(it.filterNotNull()) }
    is BranchOr -> children.map { it.toConditionOrNull() }
        .takeIf { it.isNotEmpty() && it.all { c -> c != null } }
        ?.let { Or(it.filterNotNull()) }
    is BranchNot -> child?.toConditionOrNull()?.let { Not(it) }
}

fun Condition.toBuilderNode(nextId: () -> String): BuilderNode = when (this) {
    is TextMatch -> LeafText(nextId(), field, glob)
    is PhoneMatch -> LeafPhone(nextId(), pattern)
    is Predicate -> LeafPredicate(nextId(), kind, before, source)
    is And -> BranchAnd(nextId(), of.map { it.toBuilderNode(nextId) })
    is Or -> BranchOr(nextId(), of.map { it.toBuilderNode(nextId) })
    is Not -> BranchNot(nextId(), of.toBuilderNode(nextId))
}
```

Tree edits operate by id with pure recursive helpers (also in this file):
`fun BuilderNode.replace(id: String, f: (BuilderNode) -> BuilderNode): BuilderNode`,
`fun BuilderNode.removeChild(id: String): BuilderNode`,
`fun BuilderNode.addChild(parentId: String, child: BuilderNode): BuilderNode`.
Round-trip property: `c.toBuilderNode(ids).toConditionOrNull() == c` (ids are
ignored by `Condition` equality), for representative nested conditions.

## 4. Store — `ui/RuleBuilderStore.kt` (new)

Holds editor state for ONE rule being built/edited; follows the project's
store pattern (immutable state + `StateFlow` + intent methods).

```kotlin
data class RuleBuilderState(val name: String, val root: BuilderNode)

class RuleBuilderStore(
    private val contacts: List<Contact>,
    existing: Rule? = null,        // null => new rule
) {
    // id generation: an internal incrementing counter -> stable, test-friendly ids
    // initial root for a new rule: an empty LeafText
    val state: StateFlow<RuleBuilderState>
    fun setName(name: String)
    fun changeKind(id: String, kind: NodeKind)   // convert a node to another kind
    fun addChild(parentId: String)                // append a default LeafText to a branch
    fun removeNode(id: String)                    // remove a node (no-op on root)
    fun setField(id: String, field: TextField)
    fun setGlob(id: String, glob: String)
    fun setPattern(id: String, pattern: String)
    fun setPredicateKind(id: String, kind: PredicateKind)
    fun setBefore(id: String, before: java.time.Instant?)
    fun setSource(id: String, source: Source?)

    fun toRuleOrNull(): Rule?       // Rule(name, root.toConditionOrNull()) when name blank-checked & condition valid
    val isValid: Boolean            // name non-blank && root.toConditionOrNull() != null
    fun matchCount(): Int?          // null when invalid; else RuleEngine.evaluate(contacts, RuleSet(listOf(rule))).size
}

enum class NodeKind { TEXT, PHONE, PREDICATE, AND, OR, NOT }
```

`changeKind` maps a node to a new kind, preserving children when both are branches
(AND<->OR keep children; ->NOT keeps the first child; branch->leaf drops children;
leaf->branch starts empty/with the leaf as a child is NOT required — start empty).
Keep `changeKind` behavior simple and documented; tests pin it.

## 5. Dialog — `ui/RuleBuilderDialog.kt` (new)

A Compose `Dialog`/`AlertDialog`-style modal:
- Rule **name** `TextField` (testTag `rule-name`).
- Recursive `NodeEditor(node, store)` composable:
  - **Leaf** rows: a node-kind dropdown + the kind's inputs
    (TextMatch: field dropdown + glob field; PhoneMatch: pattern field;
    Predicate: kind dropdown + optional before/source inputs for CREATED_BEFORE/SOURCE_IS).
  - **Branch** (`And`/`Or`): the kind dropdown, a column of child `NodeEditor`s
    (indented), an "add condition" button (`addChild`), and a delete button per node.
  - **Not**: kind dropdown + a single child `NodeEditor` (or "add condition" when empty).
  - Every node has a delete control except the root (root delete is a no-op / hidden).
- Live "matches N of M" line (testTag `match-count`), from `store.matchCount()`.
- **Save** button (testTag `save-rule`) disabled unless `store.isValid`; **Cancel**.
- Use stable testTags for kind dropdowns, inputs, add/delete buttons so UI tests can
  drive the tree (e.g. `node-kind:<id>`, `glob:<id>`, `add-child:<id>`, `delete:<id>`).

## 6. Deletion wiring

- `ui/DeletionReviewStore.kt` (modify): add
  `addRule(rule: Rule)` (appended, enabled), `updateRule(originalName: String, rule: Rule)`
  (replace the toggle whose rule.name == originalName, preserving its enabled flag),
  `removeRule(name: String)`. Existing `rules`, `toggleRule`, `rulesToJson`,
  `loadRules`, `run`, `commit` unchanged.
- `ui/DeletionScreen.kt` (modify): a "New rule…" button under the rules list; per
  rule row, "Edit" and "Delete" controls. Dialog open-state (`editing: Rule?` +
  `showNew: Boolean`) held in the screen; on Save, call add/updateRule and close.

## 7. Validation & errors

- Save disabled until `isValid` (name non-blank AND a complete condition tree).
- Incomplete states that make `toConditionOrNull()` null: blank glob/pattern,
  `And`/`Or` with zero children, `Not` with no child, `CREATED_BEFORE` with no date,
  `SOURCE_IS` with no source. Render a short inline hint near the offending input.
- Names: required non-blank. On create, if the name duplicates an existing rule,
  disallow Save with a hint; on edit, the rule's own name is allowed.

## 8. Testing

- **`RuleBuilderModel`** (pure, no Compose): `toConditionOrNull` for each leaf
  (valid + invalid), `And`/`Or` (empty -> null, mixed-invalid -> null, valid),
  `Not` (empty -> null, valid); `replace`/`removeChild`/`addChild` by id;
  **round-trip** `c.toBuilderNode{ids}.toConditionOrNull() == c` for a deep nested
  example (e.g. `And(of=[Or(of=[TextMatch, PhoneMatch]), Not(Predicate(NO_EMAIL))])`).
- **`RuleBuilderStore`**: setters mutate the right node by id; `changeKind`
  conversions; `isValid`/`toRuleOrNull` transitions; `matchCount` against a fixture
  contact set (build a rule that flags a known subset, assert the count); blank-name
  invalid; new vs edit construction.
- **`DeletionReviewStore`**: `addRule` appends enabled; `updateRule` replaces by
  name preserving enabled; `removeRule` drops; `rulesToJson` round-trips a built rule
  through `RuleStore`.
- **UI** (Compose, testTags): "New rule…" opens dialog; build a one-leaf rule + Save
  adds it to the rules list and it appears; "Edit" opens with the existing tree
  populated; "Delete" removes a rule; `match-count` text renders; `save-rule`
  disabled when invalid (blank glob).
- `./gradlew check` green: line ≥90 / branch ≥70, Spotless/ktlint, Konsist
  (all new code in `ui`; `core` untouched).

## 9. Scope

In scope: the `ui` mirror-tree model + conversions, `RuleBuilderStore`,
`RuleBuilderDialog`, `DeletionReviewStore` CRUD, `DeletionScreen` wiring, tests.
Out of scope: any `core/rules` change; importing rules from other formats; rule
reordering; sharing/exporting beyond the existing JSON Save…/Load….

## 10. Notes

- New rules are enabled by default so they participate in the next Run.
- The recursive `NodeEditor` is the riskiest piece; keep each node's rendering
  small and drive it entirely off the store by id to stay testable.
- YAGNI: one rule edited at a time; no multi-select, no drag-reorder of children.

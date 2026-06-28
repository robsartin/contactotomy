package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.core.rules.RuleEngine
import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.TextField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Immutable state for one rule being created or edited. */
data class RuleBuilderState(
    val name: String,
    val root: BuilderNode,
)

/**
 * Store driving the rule-builder editor for a single rule.
 *
 * - Pass [existing] to load an existing rule for editing; null creates a new rule.
 * - [contacts] are used for the live [matchCount].
 *
 * Node ids use an internal incrementing counter ("n0", "n1", …), making them
 * stable and deterministic in tests.
 */
class RuleBuilderStore(
    private val contacts: List<Contact>,
    existing: Rule? = null,
) {
    private var idCounter = 0

    private fun nextId() = "n${idCounter++}"

    private val _state =
        MutableStateFlow(
            if (existing != null) {
                RuleBuilderState(
                    name = existing.name,
                    root = existing.condition.toBuilderNode { nextId() },
                )
            } else {
                RuleBuilderState(
                    name = "",
                    root = LeafText(nextId()),
                )
            },
        )

    val state: StateFlow<RuleBuilderState> = _state.asStateFlow()

    val isValid: Boolean
        get() = _state.value.name.isNotBlank() && _state.value.root.toConditionOrNull() != null

    // ─── Intent methods ───────────────────────────────────────────────────────

    fun setName(name: String) = _state.update { it.copy(name = name) }

    /**
     * Converts the node identified by [id] to the given [kind].
     *
     * Conversion rules:
     * - AND ↔ OR: keep children.
     * - Branch → NOT: keep the first child (or null if empty).
     * - Branch → leaf: drop children.
     * - Leaf → branch: start empty (NOT starts with null child).
     */
    fun changeKind(
        id: String,
        kind: NodeKind,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        val newId = node.id
                        when (kind) {
                            NodeKind.TEXT -> LeafText(newId)
                            NodeKind.PHONE -> LeafPhone(newId)
                            NodeKind.PREDICATE -> LeafPredicate(newId)
                            NodeKind.AND ->
                                when (node) {
                                    is BranchOr -> BranchAnd(newId, node.children)
                                    is BranchNot -> BranchAnd(newId, listOfNotNull(node.child))
                                    else -> BranchAnd(newId)
                                }
                            NodeKind.OR ->
                                when (node) {
                                    is BranchAnd -> BranchOr(newId, node.children)
                                    is BranchNot -> BranchOr(newId, listOfNotNull(node.child))
                                    else -> BranchOr(newId)
                                }
                            NodeKind.NOT ->
                                when (node) {
                                    is BranchAnd -> BranchNot(newId, node.children.firstOrNull())
                                    is BranchOr -> BranchNot(newId, node.children.firstOrNull())
                                    else -> BranchNot(newId)
                                }
                        }
                    },
            )
        }
    }

    /** Appends a default [LeafText] child to the branch node identified by [parentId]. */
    fun addChild(parentId: String) {
        _state.update { st ->
            st.copy(root = st.root.addChild(parentId, LeafText(nextId())))
        }
    }

    /** Removes the node identified by [id] from the tree; no-op when [id] is the root. */
    fun removeNode(id: String) {
        if (id == _state.value.root.id) return
        _state.update { st ->
            st.copy(root = st.root.removeChild(id))
        }
    }

    fun setField(
        id: String,
        field: TextField,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        if (node is LeafText) node.copy(field = field) else node
                    },
            )
        }
    }

    fun setGlob(
        id: String,
        glob: String,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        if (node is LeafText) node.copy(glob = glob) else node
                    },
            )
        }
    }

    fun setPattern(
        id: String,
        pattern: String,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        if (node is LeafPhone) node.copy(pattern = pattern) else node
                    },
            )
        }
    }

    fun setPredicateKind(
        id: String,
        kind: PredicateKind,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        if (node is LeafPredicate) node.copy(kind = kind) else node
                    },
            )
        }
    }

    fun setBefore(
        id: String,
        before: Instant?,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        if (node is LeafPredicate) node.copy(before = before) else node
                    },
            )
        }
    }

    fun setSource(
        id: String,
        source: Source?,
    ) {
        _state.update { st ->
            st.copy(
                root =
                    st.root.replace(id) { node ->
                        if (node is LeafPredicate) node.copy(source = source) else node
                    },
            )
        }
    }

    /**
     * Returns the built [Rule] when the editor state is valid (name non-blank and
     * condition tree complete), or null otherwise.
     */
    fun toRuleOrNull(): Rule? {
        val st = _state.value
        if (st.name.isBlank()) return null
        return st.root.toConditionOrNull()?.let { Rule(st.name, it) }
    }

    /**
     * Returns the number of contacts matched by the current (valid) condition, or null
     * when the condition is not yet valid.
     */
    fun matchCount(): Int? =
        toRuleOrNull()?.let { rule ->
            RuleEngine.evaluate(contacts, RuleSet(listOf(rule))).size
        }
}

package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.core.rules.And
import com.robsartin.contactotomy.core.rules.Condition
import com.robsartin.contactotomy.core.rules.Not
import com.robsartin.contactotomy.core.rules.Or
import com.robsartin.contactotomy.core.rules.PhoneMatch
import com.robsartin.contactotomy.core.rules.Predicate
import com.robsartin.contactotomy.core.rules.PredicateKind
import com.robsartin.contactotomy.core.rules.TextField
import com.robsartin.contactotomy.core.rules.TextMatch
import java.time.Instant

/** The kind of node in the builder tree — used by UI dropdowns and [RuleBuilderStore.changeKind]. */
enum class NodeKind { TEXT, PHONE, PREDICATE, AND, OR, NOT }

/** A mutable mirror of the immutable [Condition] AST with stable string ids for tree edits. */
sealed interface BuilderNode {
    val id: String
}

data class LeafText(
    override val id: String,
    val field: TextField = TextField.EMAIL,
    val glob: String = "",
) : BuilderNode

data class LeafPhone(
    override val id: String,
    val pattern: String = "",
) : BuilderNode

data class LeafPredicate(
    override val id: String,
    val kind: PredicateKind = PredicateKind.NO_EMAIL,
    val before: Instant? = null,
    val source: Source? = null,
) : BuilderNode

data class BranchAnd(
    override val id: String,
    val children: List<BuilderNode> = emptyList(),
) : BuilderNode

data class BranchOr(
    override val id: String,
    val children: List<BuilderNode> = emptyList(),
) : BuilderNode

data class BranchNot(
    override val id: String,
    val child: BuilderNode? = null,
) : BuilderNode

// ─── Conversions ──────────────────────────────────────────────────────────────

/**
 * Converts this builder node to a [Condition], or null when the (sub)tree is incomplete
 * (e.g. blank glob/pattern, empty branch, missing date or source for parameterised predicates).
 */
fun BuilderNode.toConditionOrNull(): Condition? =
    when (this) {
        is LeafText -> glob.takeIf { it.isNotBlank() }?.let { TextMatch(field, it) }
        is LeafPhone -> pattern.takeIf { it.isNotBlank() }?.let { PhoneMatch(it) }
        is LeafPredicate ->
            when (kind) {
                PredicateKind.CREATED_BEFORE -> before?.let { Predicate(kind, before = it) }
                PredicateKind.SOURCE_IS -> source?.let { Predicate(kind, source = it) }
                else -> Predicate(kind)
            }
        is BranchAnd ->
            children
                .map { it.toConditionOrNull() }
                .takeIf { it.isNotEmpty() && it.all { c -> c != null } }
                ?.let { And(it.filterNotNull()) }
        is BranchOr ->
            children
                .map { it.toConditionOrNull() }
                .takeIf { it.isNotEmpty() && it.all { c -> c != null } }
                ?.let { Or(it.filterNotNull()) }
        is BranchNot -> child?.toConditionOrNull()?.let { Not(it) }
    }

/** Converts an immutable [Condition] to a mutable [BuilderNode], using [nextId] for node ids. */
fun Condition.toBuilderNode(nextId: () -> String): BuilderNode =
    when (this) {
        is TextMatch -> LeafText(nextId(), field, glob)
        is PhoneMatch -> LeafPhone(nextId(), pattern)
        is Predicate -> LeafPredicate(nextId(), kind, before, source)
        is And -> BranchAnd(nextId(), of.map { it.toBuilderNode(nextId) })
        is Or -> BranchOr(nextId(), of.map { it.toBuilderNode(nextId) })
        is Not -> BranchNot(nextId(), of.toBuilderNode(nextId))
    }

// ─── Tree edit helpers ────────────────────────────────────────────────────────

/**
 * Returns a copy of this node with the node matching [id] replaced by [f] applied to it.
 * If no node with [id] is found, returns this node unchanged.
 */
fun BuilderNode.replace(
    id: String,
    f: (BuilderNode) -> BuilderNode,
): BuilderNode =
    if (this.id == id) {
        f(this)
    } else {
        when (this) {
            is LeafText -> this
            is LeafPhone -> this
            is LeafPredicate -> this
            is BranchAnd -> copy(children = children.map { it.replace(id, f) })
            is BranchOr -> copy(children = children.map { it.replace(id, f) })
            is BranchNot -> copy(child = child?.replace(id, f))
        }
    }

/**
 * Returns a copy of this node with the direct or nested child matching [id] removed.
 * - In [BranchAnd]/[BranchOr]: removes the child with the given id from the children list.
 * - In [BranchNot]: sets [BranchNot.child] to null if it matches.
 * - On a leaf or non-matching node: returns this unchanged.
 */
fun BuilderNode.removeChild(id: String): BuilderNode =
    when (this) {
        is LeafText -> this
        is LeafPhone -> this
        is LeafPredicate -> this
        is BranchAnd -> {
            val newChildren = children.filter { it.id != id }
            if (newChildren.size != children.size) {
                copy(children = newChildren)
            } else {
                copy(children = children.map { it.removeChild(id) })
            }
        }
        is BranchOr -> {
            val newChildren = children.filter { it.id != id }
            if (newChildren.size != children.size) {
                copy(children = newChildren)
            } else {
                copy(children = children.map { it.removeChild(id) })
            }
        }
        is BranchNot ->
            if (child?.id == id) {
                copy(child = null)
            } else {
                copy(child = child?.removeChild(id))
            }
    }

/**
 * Returns a copy of this node with [child] appended to the node identified by [parentId].
 * - In [BranchAnd]/[BranchOr]: appends [child] to the children list of the matching node.
 * - In [BranchNot]: sets [BranchNot.child] if the id matches (ignores if already set).
 * - On a non-matching node: recurses into children.
 */
fun BuilderNode.addChild(
    parentId: String,
    child: BuilderNode,
): BuilderNode =
    when (this) {
        is LeafText -> this
        is LeafPhone -> this
        is LeafPredicate -> this
        is BranchAnd ->
            if (id == parentId) {
                copy(children = children + child)
            } else {
                copy(children = children.map { it.addChild(parentId, child) })
            }
        is BranchOr ->
            if (id == parentId) {
                copy(children = children + child)
            } else {
                copy(children = children.map { it.addChild(parentId, child) })
            }
        is BranchNot ->
            if (id == parentId) {
                if (this.child == null) copy(child = child) else this
            } else {
                copy(child = this.child?.addChild(parentId, child))
            }
    }

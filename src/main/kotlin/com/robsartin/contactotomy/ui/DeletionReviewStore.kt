package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.core.rules.RuleEngine
import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.RuleStore
import com.robsartin.contactotomy.core.rules.applyDeletions
import com.robsartin.contactotomy.core.rules.starter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds deletion-review state: rule toggles, flagged results, and approvals. */
class DeletionReviewStore(
    private val contacts: List<Contact>,
    initialRules: RuleSet = RuleSet.starter(),
) {
    private val _state =
        MutableStateFlow(
            DeletionReviewState(
                rules = initialRules.rules.map { RuleToggle(it) },
                totalContacts = contacts.size,
            ),
        )
    val state: StateFlow<DeletionReviewState> = _state.asStateFlow()

    fun toggleRule(ruleName: String) =
        _state.update { st ->
            st.copy(rules = st.rules.map { if (it.rule.name == ruleName) it.copy(enabled = !it.enabled) else it })
        }

    fun run() {
        val enabled =
            RuleSet(
                _state.value.rules
                    .filter { it.enabled }
                    .map { it.rule },
            )
        val flagged = RuleEngine.evaluate(contacts, enabled)
        _state.update { it.copy(flagged = flagged, approvedIds = emptySet(), hasRun = true) }
    }

    fun approve(id: String) =
        _state.update { st ->
            if (st.flagged.any { it.contact.id == id }) st.copy(approvedIds = st.approvedIds + id) else st
        }

    fun unapprove(id: String) = _state.update { it.copy(approvedIds = it.approvedIds - id) }

    fun approveAllForRule(ruleName: String) =
        _state.update { st ->
            val ids = st.flagged.filter { f -> f.matches.any { it.ruleName == ruleName } }.map { it.contact.id }
            st.copy(approvedIds = st.approvedIds + ids)
        }

    fun approveAll() = _state.update { st -> st.copy(approvedIds = st.flagged.map { it.contact.id }.toSet()) }

    fun clearApprovals() = _state.update { it.copy(approvedIds = emptySet()) }

    fun loadRules(json: String) =
        _state.update {
            DeletionReviewState(
                rules = RuleStore.fromJson(json).rules.map { r -> RuleToggle(r) },
                totalContacts = contacts.size,
            )
        }

    fun addRule(rule: Rule) =
        _state.update { st ->
            st.copy(rules = st.rules + RuleToggle(rule, enabled = true))
        }

    fun updateRule(
        originalName: String,
        rule: Rule,
    ) = _state.update { st ->
        st.copy(
            rules =
                st.rules.map { toggle ->
                    if (toggle.rule.name == originalName) toggle.copy(rule = rule) else toggle
                },
        )
    }

    fun removeRule(name: String) =
        _state.update { st ->
            st.copy(rules = st.rules.filter { it.rule.name != name })
        }

    fun rulesToJson(): String = RuleStore.toJson(RuleSet(_state.value.rules.map { it.rule }))

    fun commit(): List<Contact> {
        val result = applyDeletions(contacts, _state.value.approvedIds)
        _state.update { it.copy(committed = true) }
        return result
    }
}

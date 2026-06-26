package com.robsartin.contactotomy.ui

import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.rules.RuleEngine
import com.robsartin.contactotomy.core.rules.RuleSet
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
}

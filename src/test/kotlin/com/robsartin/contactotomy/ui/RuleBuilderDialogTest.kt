package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.rules.And
import com.robsartin.contactotomy.core.rules.Rule
import com.robsartin.contactotomy.core.rules.TextField
import com.robsartin.contactotomy.core.rules.TextMatch
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class RuleBuilderDialogTest {
    private val contacts =
        listOf(
            contact("a", given = "Alice", emails = listOf("alice@spam.com")),
            contact("b", given = "Bob", emails = listOf("bob@legit.com")),
        )

    private fun freshStore() = RuleBuilderStore(contacts)

    private fun andStore() =
        RuleBuilderStore(
            contacts,
            existing =
                Rule(
                    "and rule",
                    And(
                        of =
                            listOf(
                                TextMatch(TextField.EMAIL, "*@spam.com"),
                                TextMatch(TextField.NAME, "Alice"),
                            ),
                    ),
                ),
        )

    // ─── save-rule disabled when invalid ─────────────────────────────────────

    @Test
    fun `save-rule is disabled for a fresh store`() =
        runComposeUiTest {
            val store = freshStore()
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = {})
            }
            onNodeWithTag("save-rule").assertIsNotEnabled()
        }

    // ─── rule-name and glob enable Save ──────────────────────────────────────

    @Test
    fun `typing rule name and glob enables save-rule`() =
        runComposeUiTest {
            val store = freshStore()
            val rootId = store.state.value.root.id
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = {})
            }
            onNodeWithTag("save-rule").assertIsNotEnabled()
            onNodeWithTag("rule-name").performTextInput("My Rule")
            onNodeWithTag("glob:$rootId").performTextInput("*@spam.com")
            onNodeWithTag("save-rule").assertIsEnabled()
        }

    // ─── match-count is present ───────────────────────────────────────────────

    @Test
    fun `match-count node is present in the dialog`() =
        runComposeUiTest {
            val store = freshStore()
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = {})
            }
            onNodeWithTag("match-count").assertIsDisplayed()
        }

    // ─── save invokes onSave with the built rule ──────────────────────────────

    @Test
    fun `clicking save-rule calls onSave with the built Rule`() =
        runComposeUiTest {
            val store = freshStore()
            val rootId = store.state.value.root.id
            var savedRule: Rule? = null
            setContent {
                RuleBuilderDialog(store = store, onSave = { savedRule = it }, onCancel = {})
            }
            onNodeWithTag("rule-name").performTextInput("Spam")
            onNodeWithTag("glob:$rootId").performTextInput("*@spam.com")
            onNodeWithTag("save-rule").performClick()
            assertNotNull(savedRule)
            assertEquals("Spam", savedRule!!.name)
            assertEquals(TextMatch(TextField.EMAIL, "*@spam.com"), savedRule!!.condition)
        }

    // ─── cancel-rule present ──────────────────────────────────────────────────

    @Test
    fun `cancel-rule is present and calls onCancel`() =
        runComposeUiTest {
            var cancelled = false
            val store = freshStore()
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = { cancelled = true })
            }
            onNodeWithTag("cancel-rule").assertIsDisplayed().performClick()
            assertEquals(true, cancelled)
        }

    // ─── branch: add-child and delete ────────────────────────────────────────

    @Test
    fun `add-child button on an AND root adds a child editor`() =
        runComposeUiTest {
            val store = andStore()
            val rootId = store.state.value.root.id
            val initialChildCount = (store.state.value.root as BranchAnd).children.size
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = {})
            }
            onNodeWithTag("add-child:$rootId").performClick()
            val newCount = (store.state.value.root as BranchAnd).children.size
            assertEquals(initialChildCount + 1, newCount)
        }

    @Test
    fun `delete button on a child removes it`() =
        runComposeUiTest {
            val store = andStore()
            val initialChildren = (store.state.value.root as BranchAnd).children
            assertEquals(2, initialChildren.size)
            val firstChildId = initialChildren.first().id
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = {})
            }
            onNodeWithTag("delete:$firstChildId").performClick()
            val remaining = (store.state.value.root as BranchAnd).children
            assertEquals(1, remaining.size)
            assertEquals(initialChildren[1].id, remaining.first().id)
        }

    // ─── node-kind dropdown present ───────────────────────────────────────────

    @Test
    fun `node-kind dropdown is present for the root node`() =
        runComposeUiTest {
            val store = freshStore()
            val rootId = store.state.value.root.id
            setContent {
                RuleBuilderDialog(store = store, onSave = {}, onCancel = {})
            }
            onNodeWithTag("node-kind:$rootId").assertIsDisplayed()
        }
}

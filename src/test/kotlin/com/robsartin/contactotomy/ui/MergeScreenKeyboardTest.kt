package com.robsartin.contactotomy.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UI tests for keyboard accelerators in Section 1 of ReviewScreen (MergeScreen list).
 *
 * Keymap (spec §2):
 *   ↓/J — select next item
 *   ↑/K — select previous item
 *   A/Enter — accept selected
 *   R — reject selected
 *   D — discard selected
 *
 * Keys must NOT fire while an OutlinedTextField has focus.
 */
@OptIn(ExperimentalTestApi::class)
class MergeScreenKeyboardTest {
    // Two contacts that the matcher will pair as duplicates (same phone + name variants in dictionary).
    private val a1 = contact("a1", given = "Rob", family = "Sartin", phones = listOf("+15125551111"))
    private val a2 = contact("a2", given = "Robert", family = "Sartin", phones = listOf("+15125551111"))

    // A second pair: Bill/William are in the nickname dictionary, same family, same phone.
    private val b1 = contact("b1", given = "Bill", family = "Jones", phones = listOf("+15125552222"))
    private val b2 = contact("b2", given = "William", family = "Jones", phones = listOf("+15125552222"))

    /** Store with two merge clusters so navigation can be tested. */
    private fun twoClusterStore(): MergeReviewStore = MergeReviewStore(listOf(a1, a2, b1, b2))

    @Test
    fun `Down arrow moves selection to the next pending item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            val items = store.state.value.items
            // Initially first item is selected. Click on first item explicitly to ensure selection.
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.DirectionDown) }
            // After Down, the second item should be selected.
            // We can verify by pressing A (accept) and checking that the second item was accepted.
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.A) }
            assertEquals(
                Decision.ACCEPT,
                store.state.value.items[1]
                    .decision,
            )
        }

    @Test
    fun `J key moves selection to the next pending item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.J) }
            // Accept via Enter to confirm selection moved
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.Enter) }
            assertEquals(
                Decision.ACCEPT,
                store.state.value.items[1]
                    .decision,
            )
        }

    @Test
    fun `A key accepts the currently selected item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.A) }
            assertEquals(
                Decision.ACCEPT,
                store.state.value.items[0]
                    .decision,
            )
        }

    @Test
    fun `Enter key accepts the currently selected item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.Enter) }
            assertEquals(
                Decision.ACCEPT,
                store.state.value.items[0]
                    .decision,
            )
        }

    @Test
    fun `R key rejects the currently selected item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.R) }
            assertEquals(
                Decision.REJECT,
                store.state.value.items[0]
                    .decision,
            )
        }

    @Test
    fun `D key discards the currently selected item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.D) }
            assertEquals(
                Decision.DISCARD,
                store.state.value.items[0]
                    .decision,
            )
        }

    @Test
    fun `Up arrow moves selection to the previous item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            // Move to second item first
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.DirectionDown) }
            // Move back up
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.DirectionUp) }
            // Accept — first item should be accepted (selection is back on item[0])
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.A) }
            assertEquals(
                Decision.ACCEPT,
                store.state.value.items[0]
                    .decision,
            )
        }

    @Test
    fun `K key moves selection to the previous item`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.J) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.K) }
            onNodeWithTag("merge-list-container").performKeyInput { pressKey(Key.A) }
            assertEquals(
                Decision.ACCEPT,
                store.state.value.items[0]
                    .decision,
            )
        }

    @Test
    fun `key does not fire when OutlinedTextField is focused`() =
        runComposeUiTest {
            val store = twoClusterStore()
            setContent { MergeScreen(store) }
            // Click into the given-name edit field to give it focus
            onNodeWithTag("name-given").performClick()
            // Press A — should type into the field, not accept the selected item
            onNodeWithTag("name-given").performKeyInput { pressKey(Key.A) }
            // No item should have been accepted
            store.state.value.items.forEach { item ->
                assertEquals(
                    Decision.PENDING,
                    item.decision,
                    "Item ${item.id} should remain PENDING when keyboard is captured by text field",
                )
            }
        }
}

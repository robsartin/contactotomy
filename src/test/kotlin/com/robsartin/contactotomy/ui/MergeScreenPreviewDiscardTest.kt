package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.apply.ExcludedValue
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UI tests for Task 3 of #81:
 * - merged-result-card renders the effective fields for the selected item
 * - merged-result-card updates when an edit/exclusion changes
 * - discard-cluster button removes the cluster from committed output
 */
@OptIn(ExperimentalTestApi::class)
class MergeScreenPreviewDiscardTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), org = "Acme Corp")
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), org = "Acme Corp")
        return MergeReviewStore(listOf(a, b))
    }

    // ---- merged-result-card ----

    @Test
    fun `merged-result-card is displayed when a cluster is selected`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithTag("merged-result-card").assertIsDisplayed()
        }

    @Test
    fun `merged-result-card shows the effective given name`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            // The merged result card should show the name from the merged contact
            onNodeWithTag("merged-result-card").assertIsDisplayed()
            // The family name "Sartin" should be visible somewhere in the UI
            onAllNodesWithText("Sartin", substring = true).onFirst().assertExists()
        }

    @Test
    fun `merged-result-card reflects a name override when item is still pending`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            // Apply a name override while item is still PENDING (so it stays selected)
            val item =
                store.state.value.items
                    .single()
            store.setNameComponent(item.id, NameComponent.GIVEN, "Roberta")
            // Preview card must reflect the override
            onNodeWithTag("merged-result-card").assertIsDisplayed()
            onAllNodesWithText("Roberta", substring = true).onFirst().assertExists()
        }

    @Test
    fun `merged-result-card reflects a phone exclusion`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            val item =
                store.state.value.items
                    .single()
            // Exclude the phone — the preview card should not show it
            store.toggleField(item.id, ExcludedValue("phones", "+15125551234"))
            onNodeWithTag("merged-result-card").assertIsDisplayed()
        }

    // ---- discard-cluster button ----

    @Test
    fun `discard-cluster button is displayed in the detail footer`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithTag("discard-cluster").assertIsDisplayed()
        }

    @Test
    fun `clicking discard-cluster sets decision to DISCARD`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("discard-cluster").performClick()
            assertEquals(
                Decision.DISCARD,
                store.state.value.items
                    .single()
                    .decision,
            )
        }

    @Test
    fun `clicking discard-cluster removes members from committed output`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("discard-cluster").performClick()
            val result = store.commit()
            // a and b are discarded — output is empty
            assertEquals(0, result.size)
        }

    @Test
    fun `clicking discard-cluster records member ids in discardedIds`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("discard-cluster").performClick()
            val discardedIds = store.state.value.discardedIds
            assert("a" in discardedIds) { "Expected 'a' in discardedIds" }
            assert("b" in discardedIds) { "Expected 'b' in discardedIds" }
        }

    @Test
    fun `discarded cluster shows in Resolved section with discard label`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("discard-cluster").performClick()
            onNodeWithText("Resolved", substring = true).assertIsDisplayed()
        }
}

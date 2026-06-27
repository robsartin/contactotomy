package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MergeScreenDeleteTest {
    // c & d are lone singletons; creates a manual merge in the store before rendering.
    private fun storeWithManualMerge(): Pair<MergeReviewStore, String> {
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        val store = MergeReviewStore(listOf(c, d))
        val manualId = store.manualMerge(listOf("c", "d"))!!
        return store to manualId
    }

    @Test
    fun `Delete button is shown in the detail footer when a cluster is selected`() =
        runComposeUiTest {
            val (store, _) = storeWithManualMerge()
            setContent { MergeScreen(store) }
            onNodeWithText("🗑 Delete").assertIsDisplayed()
        }

    @Test
    fun `clicking Delete removes the cluster row from the list`() =
        runComposeUiTest {
            val (store, _) = storeWithManualMerge()
            setContent { MergeScreen(store) }
            onNodeWithText("🗑 Delete").performClick()
            assertEquals(0, store.state.value.items.size)
            onNodeWithText("Needs review (0)").assertIsDisplayed()
        }

    @Test
    fun `clicking Delete on a manual item returns cards to the eligible pool`() =
        runComposeUiTest {
            val (store, _) = storeWithManualMerge()
            setContent { MergeScreen(store) }
            // Before delete: eligible pool is empty (c & d are in the manual item)
            assertEquals(emptyList(), store.eligibleForManualMerge())
            onNodeWithText("🗑 Delete").performClick()
            val eligibleIds = store.eligibleForManualMerge().map { it.id }.toSet()
            assertEquals(setOf("c", "d"), eligibleIds)
        }
}

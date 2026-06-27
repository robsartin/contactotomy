package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MergeScreenManualMergeTest {
    // c & d are lone singletons; no auto clusters.
    private fun store(): MergeReviewStore {
        val c = contact("c", given = "Morgan", family = "Quill")
        val d = contact("d", given = "Devon", family = "Vasquez")
        return MergeReviewStore(listOf(c, d))
    }

    @Test
    fun `a created manual item is tagged manual in the list`() =
        runComposeUiTest {
            val store = store()
            store.manualMerge(listOf("c", "d"))
            setContent { MergeScreen(store) }
            onNodeWithText("manual", substring = true).assertIsDisplayed()
        }

    @Test
    fun `manual merge button opens a picker that filters and creates a merge`() =
        runComposeUiTest {
            val store = store()
            setContent { MergeScreen(store) }

            onNodeWithText("+ Manual merge").performClick() // open picker
            onNodeWithText("Search").assertIsDisplayed() // picker is shown

            onNodeWithText("Morgan Quill", substring = true).performClick()
            onNodeWithText("Devon Vasquez", substring = true).performClick()
            onNodeWithText("Create merge").performClick()

            onNodeWithText("manual", substring = true).assertIsDisplayed()
            assertEquals(1, store.state.value.items.size)
            assertEquals(
                Origin.MANUAL,
                store.state.value.items
                    .single()
                    .origin,
            )
        }

    @Test
    fun `picker search narrows the eligible list`() =
        runComposeUiTest {
            val store = store()
            setContent { MergeScreen(store) }
            onNodeWithText("+ Manual merge").performClick()
            onNodeWithText("Search").performTextInput("Morgan")
            onNodeWithText("Morgan Quill", substring = true).assertIsDisplayed()
            onAllNodesWithText("Devon Vasquez", substring = true).assertCountEquals(0)
        }
}

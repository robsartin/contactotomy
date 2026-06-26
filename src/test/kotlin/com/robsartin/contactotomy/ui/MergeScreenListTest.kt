package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MergeScreenListTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", "Rob", "Sartin", listOf("+15125551234"))
        val b = contact("b", "Robert", "Sartin", listOf("+15125551234"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `shows the needs-review section with the cluster`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithText("Needs review", substring = true).assertExists()
            onAllNodesWithText("Sartin", substring = true).onFirst().assertExists()
        }

    @Test
    fun `deciding moves a cluster to the Resolved section`() =
        runComposeUiTest {
            val s = dupStore()
            setContent { MergeScreen(s) }
            // the single pending cluster auto-selects; accept it from the detail pane
            onNodeWithText("Accept merge", substring = true).performClick()
            onNodeWithText("Resolved", substring = true).assertExists()
            assertEquals(
                Decision.ACCEPT,
                s.state.value.items
                    .single()
                    .decision,
            )
        }

    @Test
    fun `undo returns a resolved cluster to needs-review`() =
        runComposeUiTest {
            val s = dupStore()
            setContent { MergeScreen(s) }
            onNodeWithText("Accept merge", substring = true).performClick()
            onNodeWithText("Resolved", substring = true).assertExists()

            onNodeWithText("Undo", substring = true).performClick()

            onNodeWithText("Needs review (1)", substring = true).assertExists()
            assertEquals(
                Decision.PENDING,
                s.state.value.items
                    .single()
                    .decision,
            )
        }
}

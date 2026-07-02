package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DeletionSummaryHeadlineTest {
    private fun store() =
        DeletionReviewStore(
            listOf(
                contact("a", given = "Al", emails = listOf("no-reply@example.com")),
                contact("b", given = "Bo", emails = listOf("bo@personal.com")),
            ),
        )

    @Test
    fun `deletion summary headline appears after Run with flagged count`() =
        runComposeUiTest {
            val store = store()
            setContent { DeletionScreen(store) }
            onNodeWithText("Run").performClick()
            // The summary headline node should exist after Run
            onNodeWithTag("deletion-summary-headline").assertExists()
        }

    @Test
    fun `deletion summary headline shows flagged count text`() =
        runComposeUiTest {
            val store = store()
            setContent { DeletionScreen(store) }
            onNodeWithText("Run").performClick()
            // The headline node should contain "approved for deletion" (distinct from footer)
            onNodeWithText("approved for deletion", substring = true).assertExists()
        }

    @Test
    fun `deletion summary headline is not shown before Run`() =
        runComposeUiTest {
            val store = store()
            setContent { DeletionScreen(store) }
            onNodeWithTag("deletion-summary-headline").assertDoesNotExist()
        }
}

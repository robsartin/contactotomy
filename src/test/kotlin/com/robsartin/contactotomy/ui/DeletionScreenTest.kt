package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DeletionScreenTest {
    private fun store() =
        DeletionReviewStore(
            listOf(
                contact("a", given = "Al", emails = listOf("no-reply@example.com")),
                contact("b", given = "Bo", emails = listOf("bo@personal.com")),
            ),
        )

    @Test
    fun `run shows the flagged contact and approving updates the summary`() =
        runComposeUiTest {
            val store = store()
            setContent { DeletionScreen(store) }

            onNodeWithText("Run").performClick()
            onNodeWithText("Al", substring = true).assertExists() // flagged contact appears
            onAllNodesWithText("Approve all", substring = true).onFirst().performClick()
            onNodeWithText("1 approved", substring = true).assertExists()
        }
}

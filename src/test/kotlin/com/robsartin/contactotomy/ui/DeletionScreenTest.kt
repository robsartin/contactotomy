package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DeletionScreenTest {
    private fun store() =
        DeletionReviewStore(
            listOf(
                contact("a", given = "Al", emails = listOf("al@indeed.com")),
                contact("b", given = "Bo", emails = listOf("bo@personal.com")),
            ),
        )

    @Test
    fun `run shows the flagged contact, approve and apply commits the deletion`() =
        runComposeUiTest {
            val store = store()
            var committed: List<Contact>? = null
            setContent { DeletionScreen(store, onCommit = { committed = it }) }

            onNodeWithText("Run").performClick()
            onNodeWithText("Al", substring = true).assertExists() // flagged contact appears
            onAllNodesWithText("Approve all", substring = true).onFirst().performClick()
            onNodeWithText("Apply deletions", substring = true).performClick()

            assertEquals(listOf("b"), committed?.map { it.id })
        }
}

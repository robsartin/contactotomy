package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeBeforeCardsTest {
    private fun store(): MergeReviewStore {
        // Apple + Google card for the same person (shared phone + name) => HIGH cluster.
        val apple =
            contact(
                "apple",
                "Robert",
                "Sartin",
                phones = listOf("+15125551234"),
                source = Source.APPLE,
            )
        val google =
            contact(
                "google",
                "Robert",
                "Sartin",
                phones = listOf("+15125551234", "+15125559999"),
                source = Source.GOOGLE,
            )
        return MergeReviewStore(listOf(apple, google))
    }

    @Test
    fun `detail shows a before-cards panel with each member's source and a phone`() =
        runComposeUiTest {
            setContent { MergeScreen(store()) }
            // open the detail
            onAllNodesWithText("Sartin", substring = true).onFirst().performClick()
            // the before-cards section header
            onNodeWithText("Before", substring = true).assertExists()
            // a member source label
            onNodeWithText("GOOGLE", substring = true).assertExists()
            // a member's phone (rendered on the Google before-card and the merged chip)
            onAllNodesWithText("+15125559999", substring = true).onFirst().assertExists()
        }
}

package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MergeDetailTest {
    private fun store(): MergeReviewStore {
        // Same single phone + email on both so the merged result has exactly one phone
        // checkbox (rendered first) and one email checkbox; org differs => a real conflict.
        val a =
            contact(
                "a",
                "Robert",
                "Sartin",
                listOf("+15125551234"),
                emails = listOf("rob@me.com"),
                org = "Acme Inc",
                modifiedAt = Instant.parse("2024-01-01T00:00:00Z"),
            )
        val b =
            contact(
                "b",
                "Robert",
                "Sartin",
                listOf("+15125551234"),
                emails = listOf("rob@me.com"),
                org = "Acme",
                modifiedAt = Instant.parse("2021-01-01T00:00:00Z"),
            )
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `detail shows source cards and a phone checkbox, and toggling updates state`() =
        runComposeUiTest {
            val s = store()
            setContent { MergeScreen(s) }
            // select the cluster (text appears in both list and source card → take first)
            onAllNodesWithText("Sartin", substring = true).onFirst().performClick()
            // the source-cards section is shown
            onNodeWithText("Source cards", substring = true).assertExists()
            // the merged phone renders (source card line + merged checkbox row → assert at least one)
            onAllNodesWithText("+15125551234", substring = true).onFirst().assertExists()
            // toggle the first toggleable control — the phone checkbox is rendered before emails
            onAllNodes(isToggleable()).onFirst().performClick()
            assertTrue(
                s.state.value.items
                    .single()
                    .excludedValues
                    .any { it.value == "+15125551234" },
            )
            // org conflict choice present (source card line + conflict radio → assert at least one)
            onAllNodesWithText("Acme Inc", substring = true).onFirst().assertExists()
        }
}

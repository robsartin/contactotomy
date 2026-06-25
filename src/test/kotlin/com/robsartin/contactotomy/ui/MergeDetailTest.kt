package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import java.time.Instant
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeDetailTest {
    private fun store(): MergeReviewStore {
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
                listOf("+15125559999"),
                emails = listOf("rob@gmail.com"),
                org = "Acme",
                modifiedAt = Instant.parse("2021-01-01T00:00:00Z"),
            )
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `detail shows merged values and a conflict, and toggling updates state`() =
        runComposeUiTest {
            val s = store()
            setContent { MergeScreen(s, onCommit = {}) }
            // open the detail
            onNodeWithText("Sartin", substring = true).performClick()
            // a merged phone include/exclude chip is shown (the ☑-prefixed button,
            // distinct from the before-cards phone line)
            onNodeWithText("☑ +15125551234", substring = true).assertExists()
            // exclude it
            onNodeWithText("☑ +15125551234", substring = true).performClick()
            kotlin.test.assertTrue(
                s.state.value.items
                    .single()
                    .excludedValues
                    .any { it.value == "+15125551234" },
            )
            // org conflict choice present
            onNodeWithText("Acme Inc", substring = true).assertExists()
        }
}

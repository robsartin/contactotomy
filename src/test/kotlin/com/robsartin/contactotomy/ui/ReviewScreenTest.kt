package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UI tests for ReviewScreen: verifies both section headers render, Section 1 merge
 * interactions work, Section 2 clean rows are shown with toggle + hint, and
 * empty states are correct.
 */
@OptIn(ExperimentalTestApi::class)
class ReviewScreenTest {
    private val rob = contact("rob", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
    private val robert = contact("robert", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
    private val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc"))
    private val emailOnly = contact("email", emails = listOf("lonely@example.com"))
    private val jane = contact("jane", given = "Jane", family = "Smith", phones = listOf("+15125559999"))

    private fun fullStore() = ReviewStore(listOf(rob, robert, acme, emailOnly, jane))
    private fun noMergeStore() = ReviewStore(listOf(acme, emailOnly, jane))
    private fun noCleanStore() = ReviewStore(listOf(rob, robert))

    @Test
    fun `both section headers render`() =
        runComposeUiTest {
            setContent { ReviewScreen(fullStore()) }
            onNodeWithText("Duplicates to merge", substring = true).assertIsDisplayed()
            onNodeWithText("Cards to clean", substring = true).assertIsDisplayed()
        }

    @Test
    fun `section 1 shows merge cluster and Accept merge button`() =
        runComposeUiTest {
            setContent { ReviewScreen(fullStore()) }
            onAllNodesWithText("Sartin", substring = true).onFirst().assertExists()
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed()
        }

    @Test
    fun `accepting a merge item updates the merge store`() =
        runComposeUiTest {
            val store = fullStore()
            setContent { ReviewScreen(store) }
            onAllNodesWithText("Sartin", substring = true).onFirst().performClick()
            onNodeWithText("Accept merge", substring = true).performClick()
            assertEquals(
                Decision.ACCEPT,
                store.mergeStore.state.value.items.single().decision,
            )
        }

    @Test
    fun `section 2 shows clean candidate rows with hints`() =
        runComposeUiTest {
            setContent { ReviewScreen(fullStore()) }
            // Company-name hint for acme
            onNodeWithText("→ org: Acme Inc", substring = true).assertIsDisplayed()
            // Email-name hint for emailOnly
            onNodeWithText("→ name: lonely@example.com", substring = true).assertIsDisplayed()
        }

    @Test
    fun `toggling a clean row updates the store mark`() =
        runComposeUiTest {
            val store = fullStore()
            setContent { ReviewScreen(store) }
            // acme is pre-marked; toggle it off
            onNodeWithTag("mark:acme").performClick()
            assertTrue("acme" !in store.markedIds)
            // toggle it back on
            onNodeWithTag("mark:acme").performClick()
            assertTrue("acme" in store.markedIds)
        }

    @Test
    fun `empty state Section 1 when no duplicates`() =
        runComposeUiTest {
            setContent { ReviewScreen(noMergeStore()) }
            onNodeWithText("No duplicates found", substring = true).assertIsDisplayed()
        }

    @Test
    fun `empty state Section 2 when no clean candidates`() =
        runComposeUiTest {
            setContent { ReviewScreen(noCleanStore()) }
            onNodeWithText("No single cards need cleaning", substring = true).assertIsDisplayed()
        }

    @Test
    fun `plain person (jane) does not appear in Section 2`() =
        runComposeUiTest {
            setContent { ReviewScreen(fullStore()) }
            // jane should not have a mark tag in the clean section
            onNodeWithTag("mark:jane").assertDoesNotExist()
        }
}

package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** UI tests for the (clear) radio option on single-value conflict fields. */
@OptIn(ExperimentalTestApi::class)
class MergeScreenConflictClearTest {
    /**
     * Two cards with differing notes only; same name, same phone, same org/title.
     * This ensures a single notes conflict with exactly one "(clear)" option visible.
     */
    private fun notesOnlyStore(): MergeReviewStore {
        val a =
            Contact(
                id = "a",
                source = Source.APPLE,
                name = ContactName(given = "Al", family = "Bee"),
                phones = listOf("+15125551234"),
                rawPhones = listOf("+15125551234"),
                notes = "Note A",
                rawVCard = "",
            )
        val b =
            Contact(
                id = "b",
                source = Source.APPLE,
                name = ContactName(given = "Al", family = "Bee"),
                phones = listOf("+15125551234"),
                rawPhones = listOf("+15125551234"),
                notes = "Note B",
                rawVCard = "",
            )
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `notes conflict shows (clear) option`() =
        runComposeUiTest {
            setContent { MergeScreen(notesOnlyStore()) }
            onNodeWithText("(clear)").assertIsDisplayed()
        }

    @Test
    fun `clicking (clear) sets clearedConflicts for notes`() =
        runComposeUiTest {
            val store = notesOnlyStore()
            setContent { MergeScreen(store) }
            onNodeWithText("(clear)").performClick()
            assertTrue(
                "notes" in
                    store.state.value.items
                        .single()
                        .clearedConflicts,
            )
        }

    @Test
    fun `exactly one (clear) option shown when only notes conflicts`() =
        runComposeUiTest {
            setContent { MergeScreen(notesOnlyStore()) }
            onAllNodesWithText("(clear)").assertCountEquals(1)
        }

    @Test
    fun `clicking a source value after clear removes notes from clearedConflicts`() =
        runComposeUiTest {
            val store = notesOnlyStore()
            setContent { MergeScreen(store) }
            onNodeWithText("(clear)").performClick()
            onNodeWithText("Note A").performClick()
            assertEquals(
                false,
                "notes" in
                    store.state.value.items
                        .single()
                        .clearedConflicts,
            )
            assertEquals(
                "Note A",
                store.state.value.items
                    .single()
                    .conflictChoices["notes"],
            )
        }
}

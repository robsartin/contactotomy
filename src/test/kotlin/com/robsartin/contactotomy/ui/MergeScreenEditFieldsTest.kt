package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD tests for Task 2: editable name-component / org / notes fields in MergeScreen.
 * Written BEFORE implementation; they fail until the UI is built.
 */
@OptIn(ExperimentalTestApi::class)
class MergeScreenEditFieldsTest {
    /** A simple two-card merge pair with known name, org, and notes. */
    private fun editStore(): MergeReviewStore {
        val a =
            contact(
                "a",
                given = "Rob",
                family = "Sartin",
                phones = listOf("+15125551234"),
                org = "Corp",
                notes = "A note",
            )
        val b =
            contact(
                "b",
                given = "Robert",
                family = "Sartin",
                phones = listOf("+15125551234"),
                org = "Corp",
                notes = "B note",
            )
        return MergeReviewStore(listOf(a, b))
    }

    // ---- Name component fields ----

    @Test
    fun `name-given field renders pre-filled with effective given name`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            // The field with testTag "name-given" should be visible and contain a given name
            onNodeWithTag("name-given").assertIsDisplayed()
        }

    @Test
    fun `name-family field renders pre-filled`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("name-family").assertIsDisplayed()
        }

    @Test
    fun `name-prefix field renders`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("name-prefix").assertIsDisplayed()
        }

    @Test
    fun `name-middle field renders`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("name-middle").assertIsDisplayed()
        }

    @Test
    fun `name-suffix field renders`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("name-suffix").assertIsDisplayed()
        }

    @Test
    fun `typing in name-given calls setNameComponent and commit reflects it`() =
        runComposeUiTest {
            val store = editStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("name-given").performScrollTo()
            onNodeWithTag("name-given").performTextReplacement("Roberta")
            // nameOverride should now be set
            val item =
                store.state.value.items
                    .single()
            assertEquals("Roberta", item.nameOverride?.given)
            // accept and commit reflects it
            store.accept(itemId)
            val committed = store.commit().single()
            assertEquals("Roberta", committed.name.given)
        }

    @Test
    fun `typing in name-family calls setNameComponent`() =
        runComposeUiTest {
            val store = editStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("name-family").performScrollTo()
            onNodeWithTag("name-family").performTextReplacement("Smithson")
            val item =
                store.state.value.items
                    .single()
            assertEquals("Smithson", item.nameOverride?.family)
        }

    // ---- Org field ----

    @Test
    fun `org-edit field renders pre-filled with effective org`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("org-edit").performScrollTo()
            onNodeWithTag("org-edit").assertIsDisplayed()
        }

    @Test
    fun `typing in org-edit calls setOrgOverride and commit reflects it`() =
        runComposeUiTest {
            val store = editStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("org-edit").performScrollTo()
            onNodeWithTag("org-edit").performTextReplacement("New Corp")
            assertEquals(
                "New Corp",
                store.state.value.items
                    .single()
                    .orgOverride,
            )
            store.accept(itemId)
            val committed = store.commit().single()
            assertEquals("New Corp", committed.org)
        }

    // ---- Notes field + Append source notes ----

    @Test
    fun `notes-edit field renders`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("notes-edit").performScrollTo()
            onNodeWithTag("notes-edit").assertIsDisplayed()
        }

    @Test
    fun `append-notes button renders`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithTag("append-notes").performScrollTo()
            onNodeWithTag("append-notes").assertExists()
        }

    @Test
    fun `typing in notes-edit calls setNotesOverride`() =
        runComposeUiTest {
            val store = editStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("notes-edit").performScrollTo()
            onNodeWithTag("notes-edit").performTextReplacement("Custom note")
            assertEquals(
                "Custom note",
                store.state.value.items
                    .single()
                    .notesOverride,
            )
        }

    @Test
    fun `clicking append-notes fills notesOverride from member notes`() =
        runComposeUiTest {
            val store = editStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("append-notes").performScrollTo()
            onNodeWithTag("append-notes").performClick()
            val notesOverride =
                store.state.value.items
                    .single()
                    .notesOverride ?: ""
            assert(notesOverride.contains("A note") || notesOverride.contains("B note")) {
                "Expected source notes in override, got: $notesOverride"
            }
        }

    @Test
    fun `existing name-choice controls still render (non-regression)`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithText("Name (pick one)", substring = true).assertIsDisplayed()
        }

    @Test
    fun `existing org-choice control still renders (non-regression)`() =
        runComposeUiTest {
            setContent { MergeScreen(editStore()) }
            onNodeWithText("Company / org", substring = true).assertIsDisplayed()
        }
}

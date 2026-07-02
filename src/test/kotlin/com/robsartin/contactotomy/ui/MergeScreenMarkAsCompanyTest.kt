package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * TDD tests for #80 Task 3A: "Mark as company" button in MergeScreen detail.
 *
 * The button (testTag "mark-as-company") appears in the merged-result detail pane.
 * Clicking it:
 *   - calls store.setOrgOverride(item.id, companyNameText(effectiveName))
 *   - calls store.setNameOverride(item.id, ContactName())
 * Result at commit(): org set from former name, name cleared.
 */
@OptIn(ExperimentalTestApi::class)
class MergeScreenMarkAsCompanyTest {
    /** Two-card merge with a person name ("Rob Sartin") that we will mark as company. */
    private fun personStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Rob", family = "Sartin", phones = listOf("+15125551235"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `mark-as-company button is present in the merge detail pane`() =
        runComposeUiTest {
            setContent { MergeScreen(personStore()) }
            onNodeWithTag("mark-as-company").performScrollTo()
            onNodeWithTag("mark-as-company").assertIsDisplayed()
        }

    @Test
    fun `clicking mark-as-company clears the name override and sets org to effective name`() =
        runComposeUiTest {
            val store = personStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("mark-as-company").performScrollTo()
            onNodeWithTag("mark-as-company").performClick()
            val item =
                store.state.value.items
                    .single()
            // org should be set to something derived from the effective name
            val orgOverride = item.orgOverride
            assert(!orgOverride.isNullOrBlank()) { "orgOverride should be non-blank after mark-as-company, got: $orgOverride" }
            // name override should be a blank ContactName
            assertEquals(ContactName(), item.nameOverride)
        }

    @Test
    fun `commit after mark-as-company yields org set and name cleared`() =
        runComposeUiTest {
            val store = personStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("mark-as-company").performScrollTo()
            onNodeWithTag("mark-as-company").performClick()
            store.accept(itemId)
            val committed = store.commit().single()
            // Name should be blank/empty (cleared)
            assertEquals(ContactName(), committed.name)
            // Org should be non-blank (derived from former name)
            assert(!committed.org.isNullOrBlank()) { "org should be set after mark-as-company, got: ${committed.org}" }
        }

    @Test
    fun `commit after mark-as-company sets org to the given+family of the effective name`() =
        runComposeUiTest {
            val store = personStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("mark-as-company").performScrollTo()
            onNodeWithTag("mark-as-company").performClick()
            store.accept(itemId)
            val committed = store.commit().single()
            // "Rob Sartin" should become the org (companyNameText of given+family)
            assertEquals("Rob Sartin", committed.org)
        }
}

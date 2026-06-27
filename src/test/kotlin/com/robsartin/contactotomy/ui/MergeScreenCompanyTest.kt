package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MergeScreenCompanyTest {
    // jane (person) + acme (company mis-filed as name), no orgs => one manual item, auto-selected.
    private fun janeAcmeStore(): MergeReviewStore {
        val jane = contact("jane", given = "Jane", family = "Smith", emails = listOf("jane@acme.com"))
        val acme = contact("acme", given = "Acme", family = "Inc", emails = listOf("info@acme.com"))
        val store = MergeReviewStore(listOf(jane, acme))
        store.manualMerge(listOf("jane", "acme"))
        return store
    }

    @Test
    fun `a company-like name is badged in the detail pane`() =
        runComposeUiTest {
            setContent { MergeScreen(janeAcmeStore()) }
            onNodeWithText("looks like a company", substring = true).assertIsDisplayed()
        }

    @Test
    fun `company-org control promotes a mis-filed name and (none) clears it`() =
        runComposeUiTest {
            val store = janeAcmeStore() // auto-suggest set orgChoice = "Acme Inc"
            setContent { MergeScreen(store) }
            onNodeWithText("Company / org", substring = true).assertIsDisplayed()
            onNodeWithText("Acme Inc (from name)", substring = true).assertIsDisplayed()
            onNodeWithText("(none)").performClick()
            assertEquals(
                "",
                store.state.value.items
                    .single()
                    .orgChoice,
            )
        }

    @Test
    fun `org is not shown in the generic conflict list`() =
        runComposeUiTest {
            // two cards with differing orgs -> previously an "org (pick one)" conflict row
            val a = contact("a", given = "Pat", family = "Lee", org = "Acme", phones = listOf("+15125559999"))
            val b = contact("b", given = "Pat", family = "Lee", org = "Acme Inc", phones = listOf("+15125559999"))
            val store = MergeReviewStore(listOf(a, b))
            setContent { MergeScreen(store) }
            onNodeWithText("Acme Inc").performClick()
            assertEquals(
                "Acme Inc",
                store.state.value.items
                    .single()
                    .orgChoice,
            )
            assertEquals(
                false,
                store.state.value.items
                    .single()
                    .conflictChoices
                    .containsKey("org"),
            )
            onAllNodesWithText("org (pick one)").assertCountEquals(0)
        }
}

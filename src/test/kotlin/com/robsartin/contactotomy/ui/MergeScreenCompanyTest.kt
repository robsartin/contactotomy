package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

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
}

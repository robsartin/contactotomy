package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompanyScreenTest {
    private val acme = contact("acme").copy(name = ContactName(formatted = "Acme Inc"))
    private val jane = contact("jane", given = "Jane", family = "Smith")

    @Test
    fun `a suspect row shows the to-org hint (pre-marked)`() =
        runComposeUiTest {
            setContent { CompanyScreen(CompanyReviewStore(listOf(acme, jane))) }
            onNodeWithText("→ org: Acme Inc", substring = true).assertIsDisplayed()
        }

    @Test
    fun `toggling a row updates the store`() =
        runComposeUiTest {
            val store = CompanyReviewStore(listOf(jane))
            setContent { CompanyScreen(store) }
            onNodeWithText("Jane Smith", substring = true).performClick()
            assertTrue("jane" in store.state.value.markedIds)
        }

    @Test
    fun `filter narrows the list`() =
        runComposeUiTest {
            setContent { CompanyScreen(CompanyReviewStore(listOf(acme, jane))) }
            onNodeWithText("Filter").performTextInput("Acme")
            onNodeWithText("Acme Inc", substring = true).assertIsDisplayed()
            // "Jane Smith" filtered out
            onAllNodesWithText("Jane Smith", substring = true).assertCountEquals(0)
        }
}

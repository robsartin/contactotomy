package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeScreenListTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", "Rob", "Sartin", listOf("+15125551234"))
        val b = contact("b", "Robert", "Sartin", listOf("+15125551234"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `shows the to-merge section with the cluster`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore(), onCommit = {}) }
            onNodeWithText("To merge", substring = true).assertExists()
            onNodeWithText("Sartin", substring = true).assertExists()
        }

    @Test
    fun `apply button commits and reports the merged contacts`() =
        runComposeUiTest {
            var committed: List<Contact>? = null
            setContent { MergeScreen(dupStore(), onCommit = { committed = it }) }
            onNodeWithText("Apply merges", substring = true).performClick()
            // a+b => 1 merged contact
            kotlin.test.assertEquals(1, committed?.size)
        }
}

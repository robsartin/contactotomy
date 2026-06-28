package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.core.model.toDisplayString
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MergeScreenAddressUrlTest {
    private val addr1 = PostalAddress(street = "100 Main St", city = "Austin", region = "TX", postalCode = "78701")
    private val addr2 = PostalAddress(street = "200 Oak Ave", city = "Seattle", region = "WA", postalCode = "98101")

    private fun addressStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), addresses = listOf(addr1))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), addresses = listOf(addr2))
        return MergeReviewStore(listOf(a, b))
    }

    private fun urlStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"), urls = listOf("https://rob.example.com"))
        val b =
            contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"), urls = listOf("https://robert.example.com"))
        return MergeReviewStore(listOf(a, b))
    }

    @Test
    fun `address pills render and toggling excludes the address`() =
        runComposeUiTest {
            val s = addressStore()
            setContent { MergeScreen(s) }
            onAllNodesWithText("Sartin", substring = true).onFirst().performClick()
            val displayStr = addr1.toDisplayString()
            onAllNodesWithText(displayStr, substring = true).onFirst().assertExists()
            onNodeWithTag("addresses:$displayStr").performClick()
            assertTrue(
                s.state.value.items
                    .single()
                    .excludedValues
                    .any { it.field == "addresses" && it.value == displayStr },
            )
        }

    @Test
    fun `url pills render and toggling excludes the url`() =
        runComposeUiTest {
            val s = urlStore()
            setContent { MergeScreen(s) }
            onAllNodesWithText("Sartin", substring = true).onFirst().performClick()
            onAllNodesWithText("https://rob.example.com", substring = true).onFirst().assertExists()
            onNodeWithTag("urls:https://rob.example.com").performClick()
            assertTrue(
                s.state.value.items
                    .single()
                    .excludedValues
                    .any { it.field == "urls" && it.value == "https://rob.example.com" },
            )
        }
}

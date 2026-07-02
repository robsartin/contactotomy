package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * TDD tests for Task 3: freeform add phone/email inputs + removable chips in MergeScreen.
 * Written BEFORE implementation; they fail until the UI is built.
 */
@OptIn(ExperimentalTestApi::class)
class MergeScreenAddContactInfoTest {
    private fun dupStore(): MergeReviewStore {
        val a = contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234"))
        val b = contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234"))
        return MergeReviewStore(listOf(a, b))
    }

    // ---- Add phone ----

    @Test
    fun `add-phone-input field renders`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithTag("add-phone-input").performScrollTo()
            onNodeWithTag("add-phone-input").assertIsDisplayed()
        }

    @Test
    fun `add-phone-btn renders`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithTag("add-phone-btn").performScrollTo()
            onNodeWithTag("add-phone-btn").assertIsDisplayed()
        }

    @Test
    fun `typing a phone and clicking add-phone-btn adds it to the store`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("add-phone-input").performScrollTo()
            onNodeWithTag("add-phone-input").performTextReplacement("+18005550001")
            onNodeWithTag("add-phone-btn").performScrollTo()
            onNodeWithTag("add-phone-btn").performClick()
            val item =
                store.state.value.items
                    .single()
            assertTrue(item.addedPhones.contains("+18005550001"), "Expected +18005550001 in addedPhones: ${item.addedPhones}")
        }

    @Test
    fun `added phone chip appears in the UI`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("add-phone-input").performScrollTo()
            onNodeWithTag("add-phone-input").performTextReplacement("+18005550002")
            onNodeWithTag("add-phone-btn").performScrollTo()
            onNodeWithTag("add-phone-btn").performClick()
            // chip should appear — scroll to it then assert it exists
            onNodeWithTag("added-phone:+18005550002").performScrollTo()
            onNodeWithTag("added-phone:+18005550002").assertIsDisplayed()
        }

    @Test
    fun `added phone survives commit`() =
        runComposeUiTest {
            val store = dupStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("add-phone-input").performScrollTo()
            onNodeWithTag("add-phone-input").performTextReplacement("+18005550003")
            onNodeWithTag("add-phone-btn").performScrollTo()
            onNodeWithTag("add-phone-btn").performClick()
            store.accept(itemId)
            val committed = store.commit().single()
            assertTrue(committed.phones.contains("+18005550003"), "Expected +18005550003 in committed phones: ${committed.phones}")
        }

    @Test
    fun `clicking remove on added phone chip removes it from the store`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("add-phone-input").performScrollTo()
            onNodeWithTag("add-phone-input").performTextReplacement("+18005550004")
            onNodeWithTag("add-phone-btn").performScrollTo()
            onNodeWithTag("add-phone-btn").performClick()
            // click the chip to remove it (testTag: added-phone:+18005550004)
            onNodeWithTag("added-phone:+18005550004").performScrollTo()
            onNodeWithTag("added-phone:+18005550004").performClick()
            val item =
                store.state.value.items
                    .single()
            assertTrue(
                item.addedPhones.isEmpty() || !item.addedPhones.contains("+18005550004"),
                "Expected +18005550004 removed from addedPhones: ${item.addedPhones}",
            )
        }

    // ---- Add email ----

    @Test
    fun `add-email-input field renders`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithTag("add-email-input").performScrollTo()
            onNodeWithTag("add-email-input").assertIsDisplayed()
        }

    @Test
    fun `add-email-btn renders`() =
        runComposeUiTest {
            setContent { MergeScreen(dupStore()) }
            onNodeWithTag("add-email-btn").performScrollTo()
            onNodeWithTag("add-email-btn").assertIsDisplayed()
        }

    @Test
    fun `typing an email and clicking add-email-btn adds it to the store`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("add-email-input").performScrollTo()
            onNodeWithTag("add-email-input").performTextReplacement("new@example.com")
            onNodeWithTag("add-email-btn").performScrollTo()
            onNodeWithTag("add-email-btn").performClick()
            val item =
                store.state.value.items
                    .single()
            assertTrue(item.addedEmails.contains("new@example.com"), "Expected new@example.com in addedEmails: ${item.addedEmails}")
        }

    @Test
    fun `added email chip appears in the UI`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("add-email-input").performScrollTo()
            onNodeWithTag("add-email-input").performTextReplacement("chip@example.com")
            onNodeWithTag("add-email-btn").performScrollTo()
            onNodeWithTag("add-email-btn").performClick()
            onNodeWithTag("added-email:chip@example.com").performScrollTo()
            onNodeWithTag("added-email:chip@example.com").assertIsDisplayed()
        }

    @Test
    fun `added email survives commit`() =
        runComposeUiTest {
            val store = dupStore()
            val itemId =
                store.state.value.items
                    .single()
                    .id
            setContent { MergeScreen(store) }
            onNodeWithTag("add-email-input").performScrollTo()
            onNodeWithTag("add-email-input").performTextReplacement("commit@example.com")
            onNodeWithTag("add-email-btn").performScrollTo()
            onNodeWithTag("add-email-btn").performClick()
            store.accept(itemId)
            val committed = store.commit().single()
            assertTrue(
                committed.emails.contains("commit@example.com"),
                "Expected commit@example.com in committed emails: ${committed.emails}",
            )
        }

    @Test
    fun `clicking remove on added email chip removes it from the store`() =
        runComposeUiTest {
            val store = dupStore()
            setContent { MergeScreen(store) }
            onNodeWithTag("add-email-input").performScrollTo()
            onNodeWithTag("add-email-input").performTextReplacement("remove@example.com")
            onNodeWithTag("add-email-btn").performScrollTo()
            onNodeWithTag("add-email-btn").performClick()
            // click the chip to remove it (testTag: added-email:remove@example.com)
            onNodeWithTag("added-email:remove@example.com").performScrollTo()
            onNodeWithTag("added-email:remove@example.com").performClick()
            val item =
                store.state.value.items
                    .single()
            assertTrue(
                item.addedEmails.isEmpty() || !item.addedEmails.contains("remove@example.com"),
                "Expected remove@example.com removed from addedEmails: ${item.addedEmails}",
            )
        }
}

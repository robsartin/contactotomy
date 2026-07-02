package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.ContactPhoto
import com.robsartin.contactotomy.core.model.PostalAddress
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DeletionScreenRicherCardTest {
    /** A store whose sole contact has many optional fields. */
    private fun richStore() =
        DeletionReviewStore(
            listOf(
                contact(
                    id = "rich1",
                    given = "Rich",
                    family = "Contact",
                    emails = listOf("no-reply@example.com"),
                    addresses =
                        listOf(
                            PostalAddress(
                                street = "123 Main St",
                                city = "Springfield",
                                region = "IL",
                                postalCode = "62701",
                            ),
                        ),
                    urls = listOf("https://example.com"),
                    notes = "Important note",
                    categories = listOf("myCategory"),
                    photo = ContactPhoto(base64 = "abc"),
                    org = "Acme Corp",
                    source = com.robsartin.contactotomy.core.model.Source.APPLE,
                ),
            ),
        )

    /** A store whose contact has only name + email (no optional fields). */
    private fun sparseStore() =
        DeletionReviewStore(
            listOf(
                contact(
                    id = "sparse1",
                    given = "Sparse",
                    emails = listOf("no-reply@example.com"),
                ),
            ),
        )

    private fun androidx.compose.ui.test.ComposeUiTest.runAndSelectFirst(store: DeletionReviewStore) {
        setContent { DeletionScreen(store) }
        onNodeWithText("Run").performClick()
        onAllNodesWithText("Rich", substring = true).onFirst().performClick()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.runAndSelectSparse(store: DeletionReviewStore) {
        setContent { DeletionScreen(store) }
        onNodeWithText("Run").performClick()
        onAllNodesWithText("Sparse", substring = true).onFirst().performClick()
    }

    @Test
    fun `card shows title when present`() =
        runComposeUiTest {
            val store =
                DeletionReviewStore(
                    listOf(
                        contact(
                            id = "t1",
                            given = "Title",
                            emails = listOf("no-reply@example.com"),
                        ).copy(title = "CEO"),
                    ),
                )
            setContent { DeletionScreen(store) }
            onNodeWithText("Run").performClick()
            onAllNodesWithText("Title", substring = true).onFirst().performClick()
            onNodeWithText("CEO", substring = true).assertExists()
        }

    @Test
    fun `card shows addresses via toDisplayString`() =
        runComposeUiTest {
            runAndSelectFirst(richStore())
            // PostalAddress.toDisplayString() → "123 Main St, Springfield, IL, 62701"
            onNodeWithText("123 Main St", substring = true).assertExists()
            onNodeWithText("Springfield", substring = true).assertExists()
        }

    @Test
    fun `card shows urls when present`() =
        runComposeUiTest {
            runAndSelectFirst(richStore())
            onNodeWithText("https://example.com", substring = true).assertExists()
        }

    @Test
    fun `card shows notes when present`() =
        runComposeUiTest {
            runAndSelectFirst(richStore())
            onNodeWithText("Important note", substring = true).assertExists()
        }

    @Test
    fun `card shows categories when present`() =
        runComposeUiTest {
            runAndSelectFirst(richStore())
            onNodeWithText("myCategory", substring = true).assertExists()
        }

    @Test
    fun `card shows has photo indicator when photo is present`() =
        runComposeUiTest {
            runAndSelectFirst(richStore())
            onNodeWithTag("card-detail-has-photo").assertExists()
        }

    @Test
    fun `card omits addresses section when none present`() =
        runComposeUiTest {
            runAndSelectSparse(sparseStore())
            // No address rows should appear
            onNodeWithTag("card-detail-addresses").assertDoesNotExist()
        }

    @Test
    fun `card omits urls section when none present`() =
        runComposeUiTest {
            runAndSelectSparse(sparseStore())
            onNodeWithTag("card-detail-urls").assertDoesNotExist()
        }

    @Test
    fun `card omits notes section when null`() =
        runComposeUiTest {
            runAndSelectSparse(sparseStore())
            onNodeWithTag("card-detail-notes").assertDoesNotExist()
        }

    @Test
    fun `card omits categories section when empty`() =
        runComposeUiTest {
            runAndSelectSparse(sparseStore())
            onNodeWithTag("card-detail-categories").assertDoesNotExist()
        }

    @Test
    fun `card omits has-photo indicator when photo is absent`() =
        runComposeUiTest {
            runAndSelectSparse(sparseStore())
            onNodeWithTag("card-detail-has-photo").assertDoesNotExist()
        }
}

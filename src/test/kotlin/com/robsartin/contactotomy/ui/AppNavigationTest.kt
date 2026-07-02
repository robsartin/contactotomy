package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.testsupport.contact
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    // Two cards that the matcher clusters (nickname Rob/Robert + shared phone) => one HIGH merge.
    private fun appStoreWithDuplicates(): AppStore {
        val store =
            AppStore(
                parse = { _, _ ->
                    listOf(
                        contact("a", given = "Rob", family = "Sartin", phones = listOf("+15125551234")),
                        contact("b", given = "Robert", family = "Sartin", phones = listOf("+15125551234")),
                    )
                },
            )
        runBlocking { store.importFile("f.vcf", Source.APPLE) }
        return store
    }

    private val noPickers = arrayOf(FilePicker { null }, FilePicker { null }, FilePicker { null })

    @Test
    fun `Next on Import advances to Review`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            onNodeWithText("Next").performClick()
            assertEquals(Screen.REVIEW, store.state.value.screen)
        }

    @Test
    fun `Next on Review commits the review and advances to Deletion`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.REVIEW)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onAllNodesWithText("Sartin", substring = true).onFirst().performClick() // select the cluster
            onNodeWithText("Accept merge", substring = true).performClick() // the detail-pane Accept button
            onNodeWithText("Next").performClick()

            // the two duplicates collapse to one reviewed contact, and we advance to Deletion
            assertEquals(
                1,
                store.state.value.reviewedContacts
                    ?.size,
            )
            assertEquals(Screen.DELETION, store.state.value.screen)
        }

    @Test
    fun `Next on Deletion commits and advances to Export`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.DELETION)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick()

            // default starter rules with no approvals => contacts pass through
            assertNotNull(store.state.value.finalContacts)
            assertEquals(Screen.EXPORT, store.state.value.screen)
        }

    @Test
    fun `Next is disabled on Export`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.EXPORT)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            onNodeWithText("Next").assertIsNotEnabled()
        }
}

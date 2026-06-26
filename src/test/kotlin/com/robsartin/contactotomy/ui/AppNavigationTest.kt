package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.testsupport.contact
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `global Next is disabled on the Merge screen so it cannot bypass the merge commit`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.MERGE)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            onNodeWithText("Next").assertIsNotEnabled()
        }

    @Test
    fun `global Next is disabled on the Deletion screen`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.DELETION)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            onNodeWithText("Next").assertIsNotEnabled()
        }

    @Test
    fun `Apply merges and continue applies the merge and advances`() =
        runComposeUiTest {
            val store = appStoreWithDuplicates()
            store.goTo(Screen.MERGE)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Apply merges", substring = true).performClick()

            // the two duplicates collapse to one merged contact, and we advance to Deletion
            assertEquals(
                1,
                store.state.value.mergedContacts
                    ?.size,
            )
            assertEquals(Screen.DELETION, store.state.value.screen)
        }
}

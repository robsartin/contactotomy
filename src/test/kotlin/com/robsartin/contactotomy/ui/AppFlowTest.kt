package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end App-flow integration tests: drive the whole [App] composable through
 * the real wizard with REAL data. The default [AppStore] parses with the real
 * [com.robsartin.contactotomy.core.importer.VcfImporter] reading a fixture .vcf,
 * exercising the cross-screen flow that per-screen tests miss.
 */
@OptIn(ExperimentalTestApi::class)
class AppFlowTest {
    private fun fixturePath(name: String): String = java.io.File(javaClass.classLoader.getResource("fixtures/$name")!!.toURI()).absolutePath

    private val noPickers = arrayOf(FilePicker { null }, FilePicker { null }, FilePicker { null })

    private fun importedStore(): AppStore {
        val store = AppStore()
        runBlocking { store.importFile(fixturePath("duplicates.vcf"), Source.APPLE) }
        return store
    }

    @Test
    fun `accepting a merge reduces the exported set to four`() =
        runComposeUiTest {
            val store = importedStore()
            assertEquals(5, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge
            onAllNodesWithText("Robert A Sartin", substring = true).onFirst().performClick()
            // The detail-pane Accept button sits below the fold in the scrollable detail column.
            onNodeWithText("Accept merge", substring = true).performScrollTo().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            assertEquals(
                4,
                store.state.value.finalContacts
                    ?.size,
            )
        }

    @Test
    fun `no accept leaves all five contacts`() =
        runComposeUiTest {
            val store = importedStore()
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge
            onNodeWithText("Next").performClick() // commit with zero accepted -> Deletion
            onNodeWithText("Next").performClick() // commit deletion -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            assertEquals(
                5,
                store.state.value.finalContacts
                    ?.size,
            )
        }

    @Test
    fun `deletion path removes an approved card`() =
        runComposeUiTest {
            val store = importedStore()
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge
            onAllNodesWithText("Robert A Sartin", substring = true).onFirst().performClick()
            // The detail-pane Accept button sits below the fold in the scrollable detail column.
            onNodeWithText("Accept merge", substring = true).performScrollTo().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Deletion

            onNodeWithText("Run").performClick() // starter "austin area code" flags the 512 number
            onAllNodesWithText("Approve all").onFirst().performClick()
            onNodeWithText("Next").performClick() // commit deletion -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertTrue(final.size < 4, "expected fewer than 4 after merge-then-delete, was ${final.size}")
            assertTrue(
                final.none { c -> c.phones.any { it.filter(Char::isDigit).contains("512") } },
                "no surviving contact should keep a 512 phone",
            )
        }
}

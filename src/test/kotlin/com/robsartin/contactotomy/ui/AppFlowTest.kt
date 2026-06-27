package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.exporter.VcfExporter
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            // The detail-pane Accept button is a pinned footer, visible without scrolling.
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Tidy
            onNodeWithText("Next").performClick() // Tidy (pass-through) -> Deletion
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
            onNodeWithText("Next").performClick() // commit with zero accepted -> Tidy
            onNodeWithText("Next").performClick() // Tidy (pass-through) -> Deletion
            onNodeWithText("Next").performClick() // commit deletion -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            assertEquals(
                5,
                store.state.value.finalContacts
                    ?.size,
            )
        }

    @Test
    fun `manual merge combines two un-clustered cards across the whole flow`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("manual-merge.vcf"), Source.APPLE) }
            assertEquals(2, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge (zero auto clusters)
            onNodeWithText("+ Manual merge").performClick()
            onNodeWithText("Morgan Quill", substring = true).performClick()
            onNodeWithText("Devon Vasquez", substring = true).performClick()
            onNodeWithText("Create merge").performClick()
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Tidy
            onNodeWithText("Next").performClick() // Tidy (pass-through) -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            assertEquals(
                1,
                store.state.value.finalContacts
                    ?.size,
            )
        }

    @Test
    fun `manual merge promotes a company name into org and exports it`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("name-company.vcf"), Source.APPLE) }
            assertEquals(2, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge
            onNodeWithText("+ Manual merge").performClick()
            onNodeWithText("Jane Smith", substring = true).performClick()
            onNodeWithText("Acme Inc", substring = true).performClick()
            onNodeWithText("Create merge").performClick()
            // auto-suggest: name = Jane Smith, org = Acme Inc (promoted from the company card's name)
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Tidy
            onNodeWithText("Next").performClick() // Tidy (pass-through) -> Deletion
            onNodeWithText("Next").performClick() // commit deletion -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertEquals(1, final.size)
            assertEquals("Acme Inc", final.single().org)

            val vcard = VcfExporter().export(final)
            assertTrue(vcard.contains("ORG:Acme Inc"), "exported vCard should carry the promoted org:\n$vcard")
            assertTrue(vcard.contains("FN:Jane Smith"), "exported vCard should carry the person name:\n$vcard")
        }

    @Test
    fun `company-only cluster exports ORG with no name`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("round-rock-isd.vcf"), Source.APPLE) }
            assertEquals(2, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge (one HIGH cluster, auto-suggested company-only)
            onAllNodesWithText("Round Rock ISD", substring = true).onFirst().performClick() // select the cluster
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Tidy
            onNodeWithText("Next").performClick() // Tidy (pass-through) -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertEquals(1, final.size)
            assertEquals("Round Rock ISD", final.single().org)
            assertEquals(ContactName(), final.single().name)

            val vcard = VcfExporter().export(final)
            assertTrue(vcard.contains("ORG:Round Rock ISD"), "exported vCard should carry the org:\n$vcard")
            assertFalse(vcard.contains("FN:Round Rock ISD"), "company-only card should have no FN name:\n$vcard")
        }

    @Test
    fun `deletion path removes an approved card`() =
        runComposeUiTest {
            val store = importedStore()
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge
            onAllNodesWithText("Robert A Sartin", substring = true).onFirst().performClick()
            // The detail-pane Accept button is a pinned footer, visible without scrolling.
            onNodeWithText("Accept merge", substring = true).assertIsDisplayed().performClick()
            onNodeWithText("Next").performClick() // commit merge -> Tidy
            onNodeWithText("Next").performClick() // Tidy (pass-through) -> Deletion

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

    @Test
    fun `standalone company is normalized by the Tidy step and exported as org only`() =
        runComposeUiTest {
            val store = AppStore()
            runBlocking { store.importFile(fixturePath("lone-company.vcf"), Source.APPLE) }
            assertEquals(1, store.state.value.contacts.size)
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }

            onNodeWithText("Next").performClick() // Import -> Merge (no clusters)
            onNodeWithText("Next").performClick() // commit merge (no accepts) -> Tidy
            // "Round Rock ISD" is high-precision (ISD) => pre-checked on the Tidy step
            onNodeWithText("→ org: Round Rock ISD", substring = true).assertIsDisplayed()
            onNodeWithText("Next").performClick() // commit Tidy -> Deletion
            onNodeWithText("Next").performClick() // commit deletion (no run) -> Export

            assertEquals(Screen.EXPORT, store.state.value.screen)
            val final = store.state.value.finalContacts
            assertNotNull(final)
            assertEquals(1, final.size)
            assertEquals("Round Rock ISD", final.single().org)
            assertEquals(ContactName(), final.single().name)

            val vcard = VcfExporter().export(final)
            assertTrue(vcard.contains("ORG:Round Rock ISD"), "expected ORG:\n$vcard")
            assertFalse(vcard.contains("FN:Round Rock ISD"), "company-only card should have no FN:\n$vcard")
        }
}

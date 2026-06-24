package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppShellTest {
    private fun storeWithOneContact(): AppStore {
        val store =
            AppStore(
                parse = { _, s -> listOf(Contact(id = "a", source = s, name = ContactName(given = "A"), rawVCard = "")) },
                ioDispatcher = Dispatchers.Unconfined,
            )
        runBlocking { store.importFile("f.vcf", Source.APPLE) }
        return store
    }

    @Test
    fun `shell shows the four step labels`() =
        runComposeUiTest {
            setContent { App(AppStore(parse = { _, _ -> emptyList() })) }
            onNodeWithText("Import").assertExists()
            onNodeWithText("Merge").assertExists()
            onNodeWithText("Deletion").assertExists()
            onNodeWithText("Export").assertExists()
        }

    @Test
    fun `next advances to the merge stub when contacts exist`() =
        runComposeUiTest {
            setContent { App(storeWithOneContact()) }
            onNodeWithText("Next").performClick()
            onNodeWithText("Merge review — built in 4b").assertExists()
        }
}

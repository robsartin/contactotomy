package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ImportScreenTest {
    private fun store() =
        AppStore(
            parse = { _, s ->
                listOf(
                    Contact(id = "a", source = s, name = ContactName(given = "A"), rawVCard = ""),
                    Contact(id = "b", source = s, name = ContactName(given = "B"), rawVCard = ""),
                )
            },
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `choosing an apple file shows its summary and total`() =
        runComposeUiTest {
            val store = store()
            setContent { ImportScreen(store, applePicker = FilePicker { "apple.vcf" }) }
            onNodeWithText("Choose Apple export").performClick()
            onNodeWithText("apple.vcf", substring = true).assertExists()
            onAllNodesWithText("2 contacts", substring = true).onFirst().assertExists()
        }

    @Test
    fun `cancelling the picker imports nothing`() =
        runComposeUiTest {
            val store = store()
            setContent { ImportScreen(store, applePicker = FilePicker { null }) }
            onNodeWithText("Choose Apple export").performClick()
            onNodeWithText("0 contacts", substring = true).assertExists()
        }
}

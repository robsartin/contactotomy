package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.core.model.ContactName
import com.robsartin.contactotomy.core.model.Source
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CardsTest {
    private val rob =
        Contact(
            id = "a",
            source = Source.APPLE,
            name = ContactName(given = "Rob", family = "Sartin"),
            phones = listOf("+15125551234"),
            rawVCard = "",
        )

    @Test
    fun `source card shows name and provider badge`() =
        runComposeUiTest {
            setContent { SourceCard(rob, primary = true, selected = true) }
            onNodeWithText("Rob Sartin").assertIsDisplayed()
            onNodeWithText("Apple").assertIsDisplayed()
        }

    @Test
    fun `cluster row click fires onClick`() =
        runComposeUiTest {
            var clicked = false
            setContent {
                ClusterRow(title = "Rob Sartin · 2 cards", origin = com.robsartin.contactotomy.ui.Origin.HIGH, selected = false, onClick = {
                    clicked =
                        true
                })
            }
            onNodeWithText("Rob Sartin · 2 cards").performClick()
            assertTrue(clicked)
        }
}

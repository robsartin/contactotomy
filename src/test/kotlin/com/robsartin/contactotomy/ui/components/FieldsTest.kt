package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class FieldsTest {
    @Test
    fun `labeled progress shows reviewed of total`() =
        runComposeUiTest {
            setContent { LabeledProgress(reviewed = 2, total = 6) }
            onNodeWithText("2 of 6 reviewed").assertIsDisplayed()
        }

    @Test
    fun `labeled progress handles zero total`() =
        runComposeUiTest {
            setContent { LabeledProgress(reviewed = 0, total = 0) }
            onNodeWithText("0 of 0 reviewed").assertIsDisplayed()
        }

    @Test
    fun `field group renders its header`() =
        runComposeUiTest {
            setContent { FieldGroup("Phones (keep any)") { } }
            onNodeWithText("Phones (keep any)").assertIsDisplayed()
        }

    @Test
    fun `ValuePill kept state shows label and X glyph`() =
        runComposeUiTest {
            setContent {
                ValuePill(label = "+15125551234", removed = false, onToggle = {}, tag = "phones:+15125551234")
            }
            onNodeWithTag("phones:+15125551234").assertIsDisplayed()
            onNodeWithText("+15125551234", substring = true).assertIsDisplayed()
            onNodeWithText("✕", substring = true).assertIsDisplayed()
        }

    @Test
    fun `ValuePill removed state shows label and undo glyph`() =
        runComposeUiTest {
            setContent {
                ValuePill(label = "+15125551234", removed = true, onToggle = {}, tag = "phones:+15125551234")
            }
            onNodeWithTag("phones:+15125551234").assertIsDisplayed()
            onNodeWithText("+15125551234", substring = true).assertIsDisplayed()
            onNodeWithText("↺", substring = true).assertIsDisplayed()
        }

    @Test
    fun `ValuePill click fires onToggle`() =
        runComposeUiTest {
            var toggleCount = 0
            setContent {
                ValuePill(
                    label = "rob@me.com",
                    removed = false,
                    onToggle = { toggleCount++ },
                    tag = "emails:rob@me.com",
                )
            }
            onNodeWithTag("emails:rob@me.com").performClick()
            assertEquals(1, toggleCount)
        }

    @Test
    fun `ValuePill removed click fires onToggle`() =
        runComposeUiTest {
            var toggleCount = 0
            setContent {
                ValuePill(
                    label = "rob@me.com",
                    removed = true,
                    onToggle = { toggleCount++ },
                    tag = "emails:rob@me.com",
                )
            }
            onNodeWithTag("emails:rob@me.com").performClick()
            assertEquals(1, toggleCount)
        }
}

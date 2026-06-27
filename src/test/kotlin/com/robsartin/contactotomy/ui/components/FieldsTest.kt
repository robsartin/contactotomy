package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue

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
    fun `value chip toggle fires its callback`() =
        runComposeUiTest {
            var toggled = false
            setContent { ValueChip(label = "x@y.com", selected = true, onToggle = { toggled = true }, tag = "emails:x@y.com") }
            onNodeWithTag("emails:x@y.com").performClick()
            assertTrue(toggled)
        }

    @Test
    fun `field group renders its header`() =
        runComposeUiTest {
            setContent { FieldGroup("Phones (keep any)") { } }
            onNodeWithText("Phones (keep any)").assertIsDisplayed()
        }
}

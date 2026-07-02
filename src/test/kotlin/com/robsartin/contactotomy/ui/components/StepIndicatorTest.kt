package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.ui.Screen
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class StepIndicatorTest {
    @Test
    fun `step indicator renders all four labels`() =
        runComposeUiTest {
            setContent { StepIndicator(current = Screen.IMPORT) }
            onNodeWithText("Import").assertIsDisplayed()
            onNodeWithText("Review").assertIsDisplayed()
            onNodeWithText("Deletion").assertIsDisplayed()
            onNodeWithText("Export").assertIsDisplayed()
        }

    @Test
    fun `step indicator marks current step with testTag`() =
        runComposeUiTest {
            setContent { StepIndicator(current = Screen.REVIEW) }
            onNodeWithTag("step-current:Review").assertIsDisplayed()
        }

    @Test
    fun `step indicator marks done steps with testTag`() =
        runComposeUiTest {
            setContent { StepIndicator(current = Screen.DELETION) }
            onNodeWithTag("step-done:Import").assertIsDisplayed()
            onNodeWithTag("step-done:Review").assertIsDisplayed()
        }

    @Test
    fun `step indicator marks upcoming steps with testTag`() =
        runComposeUiTest {
            setContent { StepIndicator(current = Screen.IMPORT) }
            onNodeWithTag("step-upcoming:Review").assertIsDisplayed()
            onNodeWithTag("step-upcoming:Deletion").assertIsDisplayed()
            onNodeWithTag("step-upcoming:Export").assertIsDisplayed()
        }
}

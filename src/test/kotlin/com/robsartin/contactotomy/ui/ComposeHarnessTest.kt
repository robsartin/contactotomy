package com.robsartin.contactotomy.ui

import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ComposeHarnessTest {
    @Test
    fun `compose ui test harness renders text`() =
        runComposeUiTest {
            setContent { Text("hello-contactotomy") }
            onNodeWithText("hello-contactotomy").assertIsDisplayed()
        }
}

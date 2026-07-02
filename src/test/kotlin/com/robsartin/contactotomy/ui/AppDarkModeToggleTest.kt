package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppDarkModeToggleTest {
    private val noPickers = arrayOf(FilePicker { null }, FilePicker { null }, FilePicker { null })

    @Test
    fun `dark-mode-toggle is visible in the top bar`() =
        runComposeUiTest {
            val store = AppStore(parse = { _, _ -> emptyList() })
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            onNodeWithTag("dark-mode-toggle").assertIsDisplayed()
        }

    @Test
    fun `clicking dark-mode-toggle flips darkMode in store`() =
        runComposeUiTest {
            val store = AppStore(parse = { _, _ -> emptyList() })
            setContent { App(store, noPickers[0], noPickers[1], noPickers[2]) }
            assertFalse(store.state.value.darkMode)
            onNodeWithTag("dark-mode-toggle").performClick()
            assertTrue(store.state.value.darkMode)
        }
}

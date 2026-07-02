package com.robsartin.contactotomy.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppStoreDarkModeTest {
    @Test
    fun `darkMode defaults to false`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        assertFalse(store.state.value.darkMode)
    }

    @Test
    fun `toggleDarkMode flips darkMode to true`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        store.toggleDarkMode()
        assertTrue(store.state.value.darkMode)
    }

    @Test
    fun `toggleDarkMode flips back to false`() {
        val store = AppStore(parse = { _, _ -> emptyList() })
        store.toggleDarkMode()
        store.toggleDarkMode()
        assertFalse(store.state.value.darkMode)
    }
}

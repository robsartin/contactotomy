package com.robsartin.contactotomy.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class ExportInstructionsTest {
    @Test
    fun `loads the bundled guide`() {
        val text = loadExportInstructions()
        assertTrue(text.contains("Export, clean, and import"))
        assertTrue(text.contains("Wipe each account"))
    }
}

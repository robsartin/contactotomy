package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExportScreenTest {
    @Test
    fun `shows the count and the instructions`() =
        runComposeUiTest {
            val store = ExportStore(listOf(contact("a"), contact("b")))
            setContent { ExportScreen(store, instructions = "WIPE-EACH-ACCOUNT line") }
            onNodeWithText("2", substring = true).assertExists()
            onNodeWithText("WIPE-EACH-ACCOUNT line", substring = true).assertExists()
        }

    @Test
    fun `save writes the vcard and shows the path`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val out = tempDir.resolve("contacts-clean.vcf").toFile()
        val store = ExportStore(listOf(contact("a", given = "Al", emails = listOf("al@x.com"))))
        setContent { ExportScreen(store, savePicker = FilePicker { out.absolutePath }, instructions = "guide") }

        onNodeWithText("Save cleaned vCard", substring = true).performClick()

        assertTrue(out.exists() && out.readText().contains("VERSION:3.0"))
        assertTrue(store.state.value.exportedPath == out.absolutePath)
        onNodeWithText("Exported", substring = true).assertExists()
    }
}

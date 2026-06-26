package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.rules.RuleSet
import com.robsartin.contactotomy.core.rules.RuleStore
import com.robsartin.contactotomy.core.rules.starter
import com.robsartin.contactotomy.testsupport.contact
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeletionScreenDetailTest {
    private fun store() =
        DeletionReviewStore(
            listOf(contact("a", given = "Al", emails = listOf("al@indeed.com"))),
        )

    @Test
    fun `selecting a flagged row shows its card and reason`() =
        runComposeUiTest {
            val store = store()
            setContent { DeletionScreen(store, onCommit = {}) }
            onNodeWithText("Run").performClick()
            onAllNodesWithText("Al", substring = true).onFirst().performClick()
            onNodeWithText("al@indeed.com", substring = true).assertExists()
            onNodeWithText("flagged:", substring = true).assertExists()
        }

    @Test
    fun `Load reads a rules file via the picker`(
        @TempDir tempDir: Path,
    ) = runComposeUiTest {
        val file = tempDir.resolve("rules.json").toFile()
        file.writeText(RuleStore.toJson(RuleSet(RuleSet.starter().rules.take(1))))
        val store = store()
        setContent { DeletionScreen(store, loadPicker = FilePicker { file.absolutePath }, onCommit = {}) }
        onNodeWithText("Load", substring = true).performClick()
        assertTrue(store.state.value.rules.size == 1)
    }
}

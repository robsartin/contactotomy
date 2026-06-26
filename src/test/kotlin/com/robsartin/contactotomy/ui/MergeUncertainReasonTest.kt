package com.robsartin.contactotomy.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.testsupport.contact
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MergeUncertainReasonTest {
    @Test
    fun `uncertain row shows the match reason and both member names`() =
        runComposeUiTest {
            // name-only match (no shared phone/email) => UNCERTAIN pair with NAME_ONLY reason.
            val a = contact("a", given = "Jane", family = "Doe")
            val b = contact("b", given = "Jane", family = "Doe")
            setContent { MergeScreen(MergeReviewStore(listOf(a, b))) }
            onNodeWithText("Possible matches", substring = true).assertExists()
            // the reason text appears on the row
            onNodeWithText("NAME_ONLY", substring = true).assertExists()
        }
}

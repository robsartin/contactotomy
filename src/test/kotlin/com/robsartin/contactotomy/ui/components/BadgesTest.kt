package com.robsartin.contactotomy.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.ui.Origin
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class BadgesTest {
    @Test
    fun `source badge shows the provider label`() =
        runComposeUiTest {
            setContent { SourceBadge(Source.GOOGLE) }
            onNodeWithText("Google").assertIsDisplayed()
        }

    @Test
    fun `confidence chip label by origin`() =
        runComposeUiTest {
            setContent { ConfidenceChip(Origin.UNCERTAIN) }
            onNodeWithText("maybe").assertIsDisplayed()
        }
}

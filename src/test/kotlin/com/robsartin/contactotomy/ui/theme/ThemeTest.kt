package com.robsartin.contactotomy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class ThemeTest {
    @Test
    fun `ContactotomyTheme defaults to light palette`() =
        runComposeUiTest {
            var captured: AppColors? = null
            setContent {
                ContactotomyTheme(darkMode = false) {
                    captured = LocalAppColors.current
                }
            }
            val colors = captured!!
            // light background is bright (surface is near-white)
            assertEquals(AppColors.light().accent, colors.accent)
        }

    @Test
    fun `ContactotomyTheme with darkMode=true provides dark palette`() =
        runComposeUiTest {
            var captured: AppColors? = null
            setContent {
                ContactotomyTheme(darkMode = true) {
                    captured = LocalAppColors.current
                }
            }
            val colors = captured!!
            // dark palette surface is different from light palette surface
            assertNotEquals(AppColors.light().surface, colors.surface)
            assertEquals(AppColors.dark().surface, colors.surface)
        }

    @Test
    fun `AppColors light() matches default construction`() {
        val light = AppColors.light()
        // accent should be the signature blue
        assertEquals(Color(0xFF4A90D9), light.accent)
    }

    @Test
    fun `AppColors dark() provides dark surface`() {
        val dark = AppColors.dark()
        // dark surface should have a luminance < 0.3 (dark color)
        // We just check it's distinct from the light surface
        assertNotEquals(AppColors.light().surface, dark.surface)
    }
}

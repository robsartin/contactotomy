package com.robsartin.contactotomy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App-specific colors Material's [androidx.compose.material.Colors] does not model. */
data class AppColors(
    val accent: Color = Color(0xFF4A90D9),
    val appleBadge: Color = Color(0xFF2A70C0),
    val googleBadge: Color = Color(0xFF6B6B6B),
    val highChipBg: Color = Color(0xFFE7F0FF),
    val highChipFg: Color = Color(0xFF185FA5),
    val maybeChipBg: Color = Color(0xFFFFF3D6),
    val maybeChipFg: Color = Color(0xFF854F0B),
    val manualChipBg: Color = Color(0xFFEDEDEA),
    val manualChipFg: Color = Color(0xFF5F5E5A),
    val maybeCardBg: Color = Color(0xFFFFFDF5),
    val maybeCardBorder: Color = Color(0xFFE0C070),
    val cardBorder: Color = Color(0xFFD8D8D4),
    val selectedBorder: Color = Color(0xFF4A90D9),
    val star: Color = Color(0xFFE8A000),
    val accept: Color = Color(0xFF2E7D32),
    val reject: Color = Color(0xFFC62828),
    val mergedBorder: Color = Color(0xFF2E7D32),
    val muted: Color = Color(0xFF999999),
    /** Page / window background surface color. */
    val surface: Color = Color(0xFFFFFFFF),
    /** Primary text color. */
    val onSurface: Color = Color(0xFF1A1A1A),
    /** Subtle section divider / container background. */
    val surfaceVariant: Color = Color(0xFFF5F5F7),
) {
    companion object {
        /** Light (day) palette — the original default values. */
        fun light() =
            AppColors(
                accent = Color(0xFF4A90D9),
                appleBadge = Color(0xFF2A70C0),
                googleBadge = Color(0xFF6B6B6B),
                highChipBg = Color(0xFFE7F0FF),
                highChipFg = Color(0xFF185FA5),
                maybeChipBg = Color(0xFFFFF3D6),
                maybeChipFg = Color(0xFF854F0B),
                manualChipBg = Color(0xFFEDEDEA),
                manualChipFg = Color(0xFF5F5E5A),
                maybeCardBg = Color(0xFFFFFDF5),
                maybeCardBorder = Color(0xFFE0C070),
                cardBorder = Color(0xFFD8D8D4),
                selectedBorder = Color(0xFF4A90D9),
                star = Color(0xFFE8A000),
                accept = Color(0xFF2E7D32),
                reject = Color(0xFFC62828),
                mergedBorder = Color(0xFF2E7D32),
                muted = Color(0xFF999999),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF1A1A1A),
                surfaceVariant = Color(0xFFF5F5F7),
            )

        /** Dark (night) palette — dark surfaces, lighter text, adjusted accents. */
        fun dark() =
            AppColors(
                accent = Color(0xFF6BAED9),
                appleBadge = Color(0xFF4D9ED9),
                googleBadge = Color(0xFF9E9E9E),
                highChipBg = Color(0xFF1E3A5F),
                highChipFg = Color(0xFF90C4F0),
                maybeChipBg = Color(0xFF3D2E10),
                maybeChipFg = Color(0xFFE8C57A),
                manualChipBg = Color(0xFF2E2E2C),
                manualChipFg = Color(0xFFBBBBB7),
                maybeCardBg = Color(0xFF2A2510),
                maybeCardBorder = Color(0xFF8A7840),
                cardBorder = Color(0xFF444440),
                selectedBorder = Color(0xFF6BAED9),
                star = Color(0xFFFFCC44),
                accept = Color(0xFF4CAF50),
                reject = Color(0xFFEF5350),
                mergedBorder = Color(0xFF4CAF50),
                muted = Color(0xFF888888),
                surface = Color(0xFF1E1E1E),
                onSurface = Color(0xFFE8E8E8),
                surfaceVariant = Color(0xFF2A2A2A),
            )
    }
}

val LocalAppColors = staticCompositionLocalOf { AppColors.light() }

val appColors: AppColors
    @Composable @ReadOnlyComposable
    get() = LocalAppColors.current

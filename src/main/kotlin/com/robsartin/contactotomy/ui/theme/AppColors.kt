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
)

val LocalAppColors = staticCompositionLocalOf { AppColors() }

val appColors: AppColors
    @Composable @ReadOnlyComposable
    get() = LocalAppColors.current

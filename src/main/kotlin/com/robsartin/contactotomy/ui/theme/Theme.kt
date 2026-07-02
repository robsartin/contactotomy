package com.robsartin.contactotomy.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Compact type scale for Contactotomy. */
private val ContactotomyTypography =
    Typography(
        h6 =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                letterSpacing = 0.15.sp,
            ),
        subtitle1 =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp,
            ),
        subtitle2 =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp,
            ),
        body1 =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp,
            ),
        body2 =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.25.sp,
            ),
        caption =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
            ),
        button =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 0.75.sp,
            ),
    )

/** Wraps MaterialTheme with the app palette and provides [AppColors]. */
@Composable
fun ContactotomyTheme(
    darkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val app = if (darkMode) AppColors.dark() else AppColors.light()
    val colors =
        if (darkMode) {
            darkColors(
                primary = app.accent,
                secondary = app.accent,
                error = app.reject,
                surface = app.surface,
                background = app.surface,
                onSurface = app.onSurface,
                onBackground = app.onSurface,
            )
        } else {
            lightColors(
                primary = app.accent,
                secondary = app.accent,
                error = app.reject,
                surface = app.surface,
                background = app.surface,
                onSurface = app.onSurface,
                onBackground = app.onSurface,
            )
        }
    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialTheme(colors = colors, typography = ContactotomyTypography, content = content)
    }
}

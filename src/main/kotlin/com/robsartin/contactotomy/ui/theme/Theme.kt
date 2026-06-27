package com.robsartin.contactotomy.ui.theme

import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Wraps MaterialTheme with the app palette and provides [AppColors]. */
@Composable
fun ContactotomyTheme(content: @Composable () -> Unit) {
    val app = AppColors()
    val colors =
        lightColors(
            primary = app.accent,
            secondary = app.accent,
            error = app.reject,
        )
    CompositionLocalProvider(LocalAppColors provides app) {
        MaterialTheme(colors = colors, content = content)
    }
}

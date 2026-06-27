package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.model.Source
import com.robsartin.contactotomy.ui.Origin
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun SourceBadge(source: Source) {
    val c = appColors
    val (bg, label) =
        when (source) {
            Source.APPLE -> c.appleBadge to "Apple"
            Source.GOOGLE -> c.googleBadge to "Google"
            Source.FILE -> c.googleBadge to "File"
        }
    Text(
        label,
        color = Color.White,
        fontSize = 10.sp,
        modifier =
            Modifier
                .background(bg, RoundedCornerShape(Dimens.chipRadius))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ConfidenceChip(origin: Origin) {
    val c = appColors
    val (bg, fg, label) =
        when (origin) {
            Origin.HIGH -> Triple(c.highChipBg, c.highChipFg, "HIGH")
            Origin.UNCERTAIN -> Triple(c.maybeChipBg, c.maybeChipFg, "maybe")
            Origin.MANUAL -> Triple(c.manualChipBg, c.manualChipFg, "manual")
        }
    Text(
        label,
        color = fg,
        fontSize = 10.sp,
        modifier =
            Modifier
                .background(bg, RoundedCornerShape(Dimens.chipRadius))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

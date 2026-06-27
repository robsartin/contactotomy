package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun ValuePill(
    label: String,
    removed: Boolean,
    onToggle: () -> Unit,
    tag: String,
) {
    val fg = if (removed) appColors.muted else appColors.accept
    val bgColor = if (removed) Color(0xFFF2F2F0) else Color(0xFFEAF3E9)
    val borderColor = if (removed) appColors.cardBorder else appColors.accept
    Row(
        Modifier
            .testTag(tag)
            .background(bgColor, RoundedCornerShape(Dimens.chipRadius))
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(Dimens.chipRadius))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = fg,
            textDecoration = if (removed) TextDecoration.LineThrough else null,
        )
        Text(if (removed) "  ↺" else "  ✕", fontSize = 12.sp, color = fg)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = Dimens.sm))
}

@Composable
fun FieldGroup(
    header: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = Dimens.sm)) {
        Text(header, fontSize = 10.sp, color = appColors.muted, letterSpacing = 0.08.em)
        content()
    }
}

@Composable
fun LabeledProgress(
    reviewed: Int,
    total: Int,
) {
    val fraction = if (total == 0) 0f else reviewed.toFloat() / total.toFloat()
    Column {
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth().height(6.dp))
        Text("$reviewed of $total reviewed", fontSize = 10.sp, color = appColors.muted, modifier = Modifier.padding(top = 2.dp))
    }
}

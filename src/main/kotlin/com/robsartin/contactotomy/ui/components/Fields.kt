package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ChipDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FilterChip
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ValueChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    tag: String,
    fontSize: TextUnit = 11.sp,
) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        modifier = Modifier.testTag(tag),
        colors = ChipDefaults.filterChipColors(),
    ) {
        Text(label, fontSize = fontSize)
    }
}

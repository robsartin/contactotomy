package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.ui.Origin
import com.robsartin.contactotomy.ui.displayName
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun SourceCard(
    contact: Contact,
    primary: Boolean,
    selected: Boolean,
) {
    val c = appColors
    val border = if (selected) BorderStroke(Dimens.selected, c.selectedBorder) else BorderStroke(Dimens.hairline, c.cardBorder)
    Card(border = border, shape = RoundedCornerShape(Dimens.cardRadius), modifier = Modifier.padding(end = 6.dp)) {
        Column(Modifier.padding(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(contact.source)
                if (primary) Text("  ★", color = c.star, fontSize = 11.sp)
                Text(
                    contact.modifiedAt?.toString()?.take(10) ?: "—",
                    color = c.muted,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                )
            }
            Text(displayName(contact.name), fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
            val line = (contact.phones + contact.emails + listOfNotNull(contact.org)).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, color = c.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun ClusterRow(
    title: String,
    origin: Origin,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = appColors
    val border =
        when {
            selected -> BorderStroke(Dimens.selected, c.selectedBorder)
            origin == Origin.UNCERTAIN -> BorderStroke(Dimens.hairline, c.maybeCardBorder)
            else -> BorderStroke(Dimens.hairline, c.cardBorder)
        }
    Card(
        backgroundColor = if (origin == Origin.UNCERTAIN) c.maybeCardBg else androidx.compose.material.MaterialTheme.colors.surface,
        border = border,
        shape = RoundedCornerShape(Dimens.cardRadius),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onClick() },
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.fillMaxWidth(0.78f)) // leave ~22% for the trailing ConfidenceChip
            ConfidenceChip(origin)
        }
    }
}

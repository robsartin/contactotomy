package com.robsartin.contactotomy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.ui.Screen
import com.robsartin.contactotomy.ui.theme.appColors

private val STEP_ORDER =
    listOf(
        Screen.IMPORT to "Import",
        Screen.REVIEW to "Review",
        Screen.DELETION to "Deletion",
        Screen.EXPORT to "Export",
    )

/**
 * Horizontal stepper showing progress through the four workflow steps.
 * Each step is styled as done / current / upcoming and tagged for tests.
 */
@Composable
fun StepIndicator(current: Screen) {
    val c = appColors
    val currentIdx = STEP_ORDER.indexOfFirst { it.first == current }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        STEP_ORDER.forEachIndexed { idx, (screen, label) ->
            val isDone = idx < currentIdx
            val isCurrent = idx == currentIdx

            val dotColor =
                when {
                    isDone -> c.accept
                    isCurrent -> c.accent
                    else -> c.muted.copy(alpha = 0.4f)
                }
            val textColor =
                when {
                    isDone -> c.accept
                    isCurrent -> c.accent
                    else -> c.muted
                }
            val weight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal

            val tag =
                when {
                    isDone -> "step-done:$label"
                    isCurrent -> "step-current:$label"
                    else -> "step-upcoming:$label"
                }

            Row(
                modifier = Modifier.testTag(tag).padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Dot indicator
                Box(
                    Modifier
                        .size(if (isCurrent) 10.dp else 8.dp)
                        .background(dotColor, CircleShape),
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = weight,
                    color = textColor,
                )
                if (isDone) {
                    Text("✓", fontSize = 10.sp, color = c.accept)
                }
            }

            // Separator line between steps (not after the last)
            if (idx < STEP_ORDER.lastIndex) {
                Text("–", fontSize = 10.sp, color = c.muted.copy(alpha = 0.4f))
            }
        }
    }
}

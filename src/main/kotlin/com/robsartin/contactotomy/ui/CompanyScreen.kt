package com.robsartin.contactotomy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.components.SourceBadge
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun CompanyScreen(store: CompanyReviewStore) {
    val state by store.state.collectAsState()
    var query by remember { mutableStateOf("") }
    val all = store.listed()
    val rows = all.filter { query.isBlank() || displayName(it.name).contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        SectionHeader("Mark companies")
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Filter") })
        Text("${state.markedIds.size} of ${all.size} marked as companies", Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { c ->
                val marked = c.id in state.markedIds
                Row(
                    Modifier.fillMaxWidth().clickable { store.toggle(c.id) }.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = marked, onCheckedChange = null)
                    SourceBadge(c.source)
                    Text("  ${displayName(c.name)}")
                    if (marked) {
                        Text(
                            "  → org: ${companyNameText(c.name)}",
                            color = appColors.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

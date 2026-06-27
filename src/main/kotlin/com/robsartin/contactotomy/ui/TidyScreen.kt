package com.robsartin.contactotomy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robsartin.contactotomy.core.company.companyNameText
import com.robsartin.contactotomy.core.model.Contact
import com.robsartin.contactotomy.ui.components.SectionHeader
import com.robsartin.contactotomy.ui.components.SourceBadge
import com.robsartin.contactotomy.ui.theme.Dimens
import com.robsartin.contactotomy.ui.theme.appColors

@Composable
fun TidyScreen(store: TidyStore) {
    val state by store.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val all = store.listed()
    val rows = all.filter { query.isBlank() || displayName(it.name).contains(query, ignoreCase = true) }

    Row(Modifier.fillMaxSize().padding(top = 8.dp)) {
        // --- Left: list ---
        Column(Modifier.weight(1f)) {
            SectionHeader("Tidy cards")
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Filter") })
            Text("${state.markedIds.size} of ${all.size} marked", Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(rows) { c ->
                    val marked = c.id in state.markedIds
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedId = c.id }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = marked,
                            onCheckedChange = { store.toggle(c.id) },
                            modifier = Modifier.testTag("mark:${c.id}"),
                        )
                        SourceBadge(c.source)
                        Text("  ${displayName(c.name).ifBlank { "(no name)" }}")
                        if (marked) {
                            val hint =
                                when (store.actionFor(c)) {
                                    TidyAction.EMAIL_NAME -> "→ name: ${c.emails.first()}"
                                    TidyAction.COMPANY -> "→ org: ${companyNameText(c.name)}"
                                }
                            Text("  $hint", color = appColors.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
        // --- Right: detail pane ---
        Column(Modifier.fillMaxWidth(0.42f).padding(start = 12.dp)) {
            val selected =
                rows.firstOrNull { it.id == selectedId }
                    ?: all.firstOrNull { it.id == selectedId }
            if (selected == null) {
                Text("Select a card")
            } else {
                Card(shape = RoundedCornerShape(Dimens.cardRadius)) {
                    Box(Modifier.padding(10.dp)) { TidyCardDetail(selected) }
                }
            }
        }
    }
}

@Composable
private fun TidyCardDetail(c: Contact) {
    Column {
        Text(displayName(c.name))
        if (c.emails.isNotEmpty()) Text(c.emails.joinToString())
        if (c.phones.isNotEmpty()) Text(c.phones.joinToString())
        c.org?.let { Text(it) }
        Text("source: ${c.source}")
    }
}

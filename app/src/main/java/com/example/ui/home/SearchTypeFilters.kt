package com.example.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.NoteType

@Composable
internal fun SearchTypeFilters(selected: NoteType?, onSelect: (NoteType?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf<NoteType?>(null) + NoteType.entries).forEach { type ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = {
                Text(when (type) { NoteType.TEXT -> "Notes"; NoteType.CHECKLIST -> "Checklists"; NoteType.EXPENSE -> "Expenses"; NoteType.SCRIBBLE -> "Boards"; null -> "Any type" })
            })
        }
    }
}
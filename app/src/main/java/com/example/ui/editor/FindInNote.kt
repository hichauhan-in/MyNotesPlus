package com.example.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.ReadableContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FindInNote(content: String, type: String, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember(query) { mutableStateOf(0) }
    val lines by produceState(emptyList<String>(), content, type) {
        value = withContext(Dispatchers.Default) { ReadableContent.text(content, type).lines() }
    }
    val matches = remember(lines, query) {
        if (query.isBlank()) emptyList() else lines.flatMapIndexed { index, line ->
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(line).map { index to it.range }.toList()
        }.take(2000)
    }
    val list = rememberLazyListState()
    LaunchedEffect(matches, selected) { matches.getOrNull(selected)?.let { list.animateScrollToItem(it.first) } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Find in note", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close search") }
                }
                OutlinedTextField(query, { query = it.take(256) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Find") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (matches.isEmpty()) "No matches" else "${selected + 1} of ${matches.size}${if (matches.size == 2000) "+" else ""}", modifier = Modifier.weight(1f))
                    IconButton(enabled = matches.isNotEmpty(), onClick = { selected = (selected - 1 + matches.size) % matches.size }) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
                    IconButton(enabled = matches.isNotEmpty(), onClick = { selected = (selected + 1) % matches.size }) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
                }
                val active = matches.getOrNull(selected)
                val highlight = MaterialTheme.colorScheme.secondaryContainer
                val currentHighlight = MaterialTheme.colorScheme.tertiaryContainer
                LazyColumn(state = list, modifier = Modifier.weight(1f)) {
                    itemsIndexed(lines) { index, line ->
                        Text(buildAnnotatedString {
                            append(line.ifEmpty { " " })
                            matches.filter { it.first == index }.forEach { match ->
                                addStyle(SpanStyle(background = if (match == active) currentHighlight else highlight), match.second.first, match.second.last + 1)
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
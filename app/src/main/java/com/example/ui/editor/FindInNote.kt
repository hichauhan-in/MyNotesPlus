package com.example.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.example.domain.model.ReadableContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun FindInNote(content: String, type: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    val lines by produceState(emptyList<String>(), content, type) {
        value = withContext(Dispatchers.Default) { ReadableContent.text(content, type).lines() }
    }
    var selected by remember(query, lines) { mutableStateOf(0) }
    val matches = remember(lines, query) {
        val pattern = Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        if (query.isBlank()) emptyList() else lines.asSequence().flatMapIndexed { index, line ->
            pattern.findAll(line).map { index to it.range }
        }.take(2000).toList()
    }
    val matchesByLine = remember(matches) { matches.groupBy { it.first } }
    val list = rememberLazyListState()
    LaunchedEffect(matches, selected) { matches.getOrNull(selected)?.let { list.animateScrollToItem(it.first) } }
    BackHandler(onBack = onDismiss)
    Surface(modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                EditorSearchRow(query, { query = it }, onClose = onDismiss, onNext = {
                    if (matches.isNotEmpty()) selected = (selected + 1) % matches.size
                })
                if (query.isNotBlank()) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (matches.isEmpty()) "No matches" else "${selected + 1} of ${matches.size}${if (matches.size == 2000) "+" else ""}", modifier = Modifier.weight(1f))
                    IconButton(enabled = matches.isNotEmpty(), onClick = { selected = (selected - 1 + matches.size) % matches.size }) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
                    IconButton(enabled = matches.isNotEmpty(), onClick = { selected = (selected + 1) % matches.size }) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
                }
                val active = matches.getOrNull(selected)
                val highlight = MaterialTheme.colorScheme.secondaryContainer
                val currentHighlight = MaterialTheme.colorScheme.tertiaryContainer
                LazyColumn(state = list, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    itemsIndexed(lines) { index, line ->
                        Text(buildAnnotatedString {
                            append(line.ifEmpty { " " })
                            matchesByLine[index].orEmpty().forEach { match ->
                                addStyle(SpanStyle(background = if (match == active) currentHighlight else highlight), match.second.first, match.second.last + 1)
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
    }
}
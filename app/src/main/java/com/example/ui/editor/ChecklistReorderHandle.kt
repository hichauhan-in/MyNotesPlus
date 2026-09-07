package com.example.ui.editor

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ChecklistReorderHandle(onMove: (Int) -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val move by rememberUpdatedState(onMove)
    val step = with(LocalDensity.current) { 54.dp.toPx() }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.pointerInput(step) {
            var distance = 0f
            detectDragGesturesAfterLongPress(
                onDragStart = { distance = 0f },
                onDrag = { change, amount -> change.consume(); distance += amount.y },
                onDragEnd = { move((distance / step).roundToInt()); distance = 0f },
                onDragCancel = { distance = 0f },
            )
        }.semantics {
            customActions = listOf(
                CustomAccessibilityAction("Move up") { move(-1); true },
                CustomAccessibilityAction("Move down") { move(1); true },
            )
        }) { Icon(Icons.Rounded.DragIndicator, "Reorder item") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { move(-1); open = false })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { move(1); open = false })
            DropdownMenuItem(text = { Text("Remove item") }, onClick = { onDelete(); open = false })
        }
    }
}
package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** The icon choices for user-created note templates, shared by the manager and the template editor. */
object TemplateIcons {
    val options: List<Pair<String, ImageVector>> = listOf(
        "note" to Icons.Rounded.EditNote,
        "checklist" to Icons.Rounded.Checklist,
        "meeting" to Icons.Rounded.Groups,
        "work" to Icons.Rounded.Work,
        "idea" to Icons.Rounded.Lightbulb,
        "book" to Icons.Rounded.MenuBook,
        "travel" to Icons.Rounded.Flight,
        "shopping" to Icons.Rounded.ShoppingCart,
        "fitness" to Icons.Rounded.FitnessCenter,
        "finance" to Icons.Rounded.Payments,
        "calendar" to Icons.Rounded.CalendarMonth,
        "reminder" to Icons.Rounded.Alarm,
        "study" to Icons.Rounded.School,
        "code" to Icons.Rounded.Code,
        "music" to Icons.Rounded.MusicNote,
        "food" to Icons.Rounded.Restaurant,
        "favorite" to Icons.Rounded.StarBorder,
    )

    fun iconFor(key: String): ImageVector =
        options.firstOrNull { it.first == key }?.second ?: Icons.Rounded.EditNote
}

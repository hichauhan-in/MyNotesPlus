package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Consistent, generous corner radii used across MyNotes.
 * Everything sits on an 8dp grid; corners scale with component size.
 */
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object AppShapes {
    val card = RoundedCornerShape(24.dp)
    val cardCompact = RoundedCornerShape(20.dp)
    val chip = RoundedCornerShape(50)
    val field = RoundedCornerShape(20.dp)
    val bottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(50)
    val fab = RoundedCornerShape(22.dp)
}

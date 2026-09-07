package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/**
 * App-themed switch colours. When ON, the track uses the brand purple with a **dark, theme-matching
 * circle** (the app background colour) instead of a bright white knob, so toggles feel part of the
 * neumorphic design rather than jumping out. OFF keeps the Material defaults (which already look right).
 */
@Composable
fun mynotesSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.background,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
)

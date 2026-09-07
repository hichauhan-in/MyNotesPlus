package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * MyNotes brand palette.
 *
 * The identity is a calm indigo → violet gradient with a teal accent used sparingly
 * for positive / success moments (completed checklists, saved state, etc.).
 *
 * Colors are grouped into a full Material 3 role set for both light and dark so the
 * dark theme is *redesigned* rather than a naive inversion.
 */

// ---- Brand seeds ---------------------------------------------------------------
val BrandIndigo = Color(0xFF5B5BF0)
val BrandViolet = Color(0xFF8B5CF6)
val BrandTeal = Color(0xFF14B8A6)

// ---- Light scheme --------------------------------------------------------------
val md_light_primary = Color(0xFF4F4CE8)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFE3E1FF)
val md_light_onPrimaryContainer = Color(0xFF11004D)

val md_light_secondary = Color(0xFF7C4DFF)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFEBDDFF)
val md_light_onSecondaryContainer = Color(0xFF23005C)

val md_light_tertiary = Color(0xFF0E9384)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFB8F5EA)
val md_light_onTertiaryContainer = Color(0xFF00201C)

val md_light_background = Color(0xFFF3F4FB)
val md_light_onBackground = Color(0xFF1A1B22)
val md_light_surface = Color(0xFFFAFAFF)
val md_light_onSurface = Color(0xFF1A1B22)
val md_light_surfaceVariant = Color(0xFFE4E1EC)
val md_light_onSurfaceVariant = Color(0xFF47464F)
val md_light_surfaceTint = md_light_primary
val md_light_outline = Color(0xFF787680)
val md_light_outlineVariant = Color(0xFFC9C5D0)

val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)

val md_light_surfaceContainerLowest = Color(0xFFFFFFFF)
val md_light_surfaceContainerLow = Color(0xFFF4F3FB)
val md_light_surfaceContainer = Color(0xFFEEEDF6)
val md_light_surfaceContainerHigh = Color(0xFFE8E7F1)
val md_light_surfaceContainerHighest = Color(0xFFE2E1EB)
val md_light_inverseSurface = Color(0xFF2F303A)
val md_light_inverseOnSurface = Color(0xFFF1F0FA)
val md_light_scrim = Color(0xFF000000)

// ---- Dark scheme (crafted, not inverted) --------------------------------------
val md_dark_primary = Color(0xFFC1C1FF)
val md_dark_onPrimary = Color(0xFF1C118F)
val md_dark_primaryContainer = Color(0xFF3833CF)
val md_dark_onPrimaryContainer = Color(0xFFE3E1FF)

val md_dark_secondary = Color(0xFFD3BBFF)
val md_dark_onSecondary = Color(0xFF3B0D8F)
val md_dark_secondaryContainer = Color(0xFF5A2FBD)
val md_dark_onSecondaryContainer = Color(0xFFEBDDFF)

val md_dark_tertiary = Color(0xFF5CD9C8)
val md_dark_onTertiary = Color(0xFF003731)
val md_dark_tertiaryContainer = Color(0xFF005048)
val md_dark_onTertiaryContainer = Color(0xFFB8F5EA)

val md_dark_background = Color(0xFF121218)
val md_dark_onBackground = Color(0xFFE5E1E9)
val md_dark_surface = Color(0xFF17171F)
val md_dark_onSurface = Color(0xFFE5E1E9)
val md_dark_surfaceVariant = Color(0xFF47464F)
val md_dark_onSurfaceVariant = Color(0xFFC9C5D0)
val md_dark_surfaceTint = md_dark_primary
val md_dark_outline = Color(0xFF938F9A)
val md_dark_outlineVariant = Color(0xFF47464F)

val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_surfaceContainerLowest = Color(0xFF0D0D12)
val md_dark_surfaceContainerLow = Color(0xFF17171F)
val md_dark_surfaceContainer = Color(0xFF1C1C25)
val md_dark_surfaceContainerHigh = Color(0xFF262630)
val md_dark_surfaceContainerHighest = Color(0xFF31313B)
val md_dark_inverseSurface = Color(0xFFE5E1E9)
val md_dark_inverseOnSurface = Color(0xFF2F303A)
val md_dark_scrim = Color(0xFF000000)

// ---- Neumorphic shadow tokens --------------------------------------------------
val NeuLightHighlight = Color(0xFFFFFFFF)
val NeuLightShadow = Color(0xFFC8CCE0)
val NeuDarkHighlight = Color(0xFF25252F)
val NeuDarkShadow = Color(0xFF08080C)

// ---- Note accent palette (user-selectable colour labels) ----------------------
val NoteAccents = listOf(
    Color(0xFF5B5BF0), // indigo  (default)
    Color(0xFF8B5CF6), // violet
    Color(0xFFEC4899), // pink
    Color(0xFFF43F5E), // rose
    Color(0xFFF97316), // orange
    Color(0xFFEAB308), // gold
    Color(0xFF22C55E), // green
    Color(0xFF14B8A6), // teal
    Color(0xFF0EA5E9), // sky
    Color(0xFF64748B), // slate
)

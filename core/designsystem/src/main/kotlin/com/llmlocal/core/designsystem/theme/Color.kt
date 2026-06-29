package com.llmlocal.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Primary — warm orange (the brand accent).
// Inspired by Toasted Apricot / Burnt Sienna, derived from the legacy
// `Orange40` value so previously-built screens stay close to their old hue.
// ---------------------------------------------------------------------------
val Orange10 = Color(0xFF2A0E00)
val Orange20 = Color(0xFF4A1B04)
val Orange30 = Color(0xFF6E2D0E)
val Orange40 = Color(0xFFE26A2C) // brand "Primary" — used by LightColors
val Orange50 = Color(0xFFEA7E45)
val Orange60 = Color(0xFFF1976A)
val Orange70 = Color(0xFFF7AF89)
val Orange80 = Color(0xFFFFB68A) // used by DarkColors
val Orange90 = Color(0xFFFFD7BD)
val Orange95 = Color(0xFFFFF4ED)

// ---------------------------------------------------------------------------
// Secondary — burnt caramel / toasted brown. Used for "secondary" accents
// (chips, hero gradients, ingredient tints).
// ---------------------------------------------------------------------------
val Brown10 = Color(0xFF1B0F00)
val Brown20 = Color(0xFF412E14)
val Brown30 = Color(0xFF59402A)
val Brown40 = Color(0xFF6B4F2A) // legacy "Brown40" used as light secondary
val Brown50 = Color(0xFF8A6739)
val Brown60 = Color(0xFFA4824E)
val Brown70 = Color(0xFFBE9D67)
val Brown80 = Color(0xFFE7C29C)
val Brown90 = Color(0xFFF5DCC1)
val Brown95 = Color(0xFFFFF1E0)

// ---------------------------------------------------------------------------
// Tertiary — terracotta. A complementary warm pink-red for the third tonal
// axis, so tertiary* tones aren't the M3 default purple.
// ---------------------------------------------------------------------------
val Terracotta10 = Color(0xFF2C0509)
val Terracotta20 = Color(0xFF521419)
val Terracotta30 = Color(0xFF7A2225)
val Terracotta40 = Color(0xFFA53A33)
val Terracotta50 = Color(0xFFCB5346)
val Terracotta60 = Color(0xFFE37562)
val Terracotta70 = Color(0xFFF49885)
val Terracotta80 = Color(0xFFFFB4A1)
val Terracotta90 = Color(0xFFFFDAD0)
val Terracotta95 = Color(0xFFFFEDE7)

// ---------------------------------------------------------------------------
// Neutral — "Espresso". Warm grays that match the brand palette; drive
// `surface`, `surfaceVariant`, `outline`. Replacing the M3 default cool
// neutrals removes the visual "pop" of cool purples that today's default
// surfaces against a warm-orange background.
// ---------------------------------------------------------------------------
val Espresso10 = Color(0xFF120A05)
val Espresso15 = Color(0xFF1F1208) // surface in dark scheme (legacy Orange15)
val Espresso20 = Color(0xFF2B1B0F)
val Espresso30 = Color(0xFF3D2818)
val Espresso40 = Color(0xFF54402D)
val Espresso50 = Color(0xFF6E5842)
val Espresso60 = Color(0xFF8C7159)
val Espresso70 = Color(0xFFA98E72)
val Espresso80 = Color(0xFFC5AC8E)
val Espresso90 = Color(0xFFE3CFBA)
val Espresso95 = Color(0xFFF1E2D2)
val Espresso99 = Color(0xFFFFFAF6)

// ---------------------------------------------------------------------------
// Error — warm red, slightly desaturated to match the palette.
// ---------------------------------------------------------------------------
val Red10 = Color(0xFF410002)
val Red20 = Color(0xFF690005)
val Red30 = Color(0xFF93000A)
val Red40 = Color(0xFFBA1A1A)
val Red80 = Color(0xFFFFB4AB)
val Red90 = Color(0xFFFFDAD6)

// ---------------------------------------------------------------------------
// Semantic — Sage (success). Used for the "Installed" status pill.
// ---------------------------------------------------------------------------
val Sage10 = Color(0xFF052614)
val Sage20 = Color(0xFF0E4429)
val Sage30 = Color(0xFF1A6340)
val Sage40 = Color(0xFF2A825A) // bright sage, used by the Installed dot
val Sage80 = Color(0xFFA6E5C2)
val Sage90 = Color(0xFFCCF2DA)

// ---------------------------------------------------------------------------
// Hero gradients.
//
// `heroLightGradient` / `heroDarkGradient` are three-stop vertical Brushes
// the recipe screen + no-model banner use as the background of their hero
// card. They're declared in this file (not in Theme.kt) because a Brush is
// a runtime value, not a constant, and can't live in a MaterialTheme slot.
// ---------------------------------------------------------------------------
val heroLightGradient: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            Orange95,
            Brown95,
            Espresso99,
        ),
    )

val heroDarkGradient: Brush
    get() = Brush.verticalGradient(
        colors = listOf(
            Espresso30,
            Espresso20,
            Espresso15,
        ),
    )

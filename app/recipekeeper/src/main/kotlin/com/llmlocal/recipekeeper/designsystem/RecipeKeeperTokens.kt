package com.llmlocal.recipekeeper.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmlocal.core.designsystem.theme.DesignTokens

// ---------------------------------------------------------------------------
// Brand palette — WS0 placeholder, copied from `:app:recipes`.
//
// WS5b replaces this with the Recipe Keeper brand (UX spec §8): plum
// primary, olive secondary, saffron tertiary, parchment neutrals, serif
// display type, flatter shapes. The shared design system has no opinion
// on color; this file is the only place the brand lives.
// ---------------------------------------------------------------------------

// Primary — warm orange (the brand accent).
private val Orange10 = Color(0xFF2A0E00)
private val Orange20 = Color(0xFF4A1B04)
private val Orange30 = Color(0xFF6E2D0E)
private val Orange40 = Color(0xFFE26A2C)
private val Orange50 = Color(0xFFEA7E45)
private val Orange60 = Color(0xFFF1976A)
private val Orange70 = Color(0xFFF7AF89)
private val Orange80 = Color(0xFFFFB68A)
private val Orange90 = Color(0xFFFFD7BD)
private val Orange95 = Color(0xFFFFF4ED)

// Secondary — burnt caramel / toasted brown.
private val Brown10 = Color(0xFF1B0F00)
private val Brown20 = Color(0xFF412E14)
private val Brown30 = Color(0xFF59402A)
private val Brown40 = Color(0xFF6B4F2A)
private val Brown50 = Color(0xFF8A6739)
private val Brown60 = Color(0xFFA4824E)
private val Brown70 = Color(0xFFBE9D67)
private val Brown80 = Color(0xFFE7C29C)
private val Brown90 = Color(0xFFF5DCC1)
private val Brown95 = Color(0xFFFFF1E0)

// Tertiary — terracotta.
private val Terracotta10 = Color(0xFF2C0509)
private val Terracotta20 = Color(0xFF521419)
private val Terracotta30 = Color(0xFF7A2225)
private val Terracotta40 = Color(0xFFA53A33)
private val Terracotta50 = Color(0xFFCB5346)
private val Terracotta60 = Color(0xFFE37562)
private val Terracotta70 = Color(0xFFF49885)
private val Terracotta80 = Color(0xFFFFB4A1)
private val Terracotta90 = Color(0xFFFFDAD0)
private val Terracotta95 = Color(0xFFFFEDE7)

// Neutral — warm "Espresso" grays.
private val Espresso10 = Color(0xFF120A05)
private val Espresso15 = Color(0xFF1F1208)
private val Espresso20 = Color(0xFF2B1B0F)
private val Espresso30 = Color(0xFF3D2818)
private val Espresso40 = Color(0xFF54402D)
private val Espresso50 = Color(0xFF6E5842)
private val Espresso60 = Color(0xFF8C7159)
private val Espresso70 = Color(0xFFA98E72)
private val Espresso80 = Color(0xFFC5AC8E)
private val Espresso90 = Color(0xFFE3CFBA)
private val Espresso95 = Color(0xFFF1E2D2)
private val Espresso99 = Color(0xFFFFFAF6)

// Error — warm red, slightly desaturated.
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// Semantic — Sage (success). Used by `StatusTone.Installed`.
private val Sage40 = Color(0xFF2A825A)

// ---------------------------------------------------------------------------
// Schemes — every M3 slot is filled so the app doesn't fall back to the
// `Color(0xFF6650a4)` M3 defaults (which look out of place against a warm
// orange palette).
// ---------------------------------------------------------------------------

private val LightColors: ColorScheme = lightColorScheme(
    primary = Orange40,
    onPrimary = Orange95,
    primaryContainer = Orange90,
    onPrimaryContainer = Orange10,
    inversePrimary = Orange80,

    secondary = Brown40,
    onSecondary = Brown95,
    secondaryContainer = Brown90,
    onSecondaryContainer = Brown10,

    tertiary = Terracotta40,
    onTertiary = Terracotta95,
    tertiaryContainer = Terracotta90,
    onTertiaryContainer = Terracotta10,

    background = Espresso99,
    onBackground = Espresso10,

    surface = Espresso99,
    onSurface = Espresso10,
    surfaceVariant = Espresso95,
    onSurfaceVariant = Espresso40,
    surfaceTint = Orange40,
    inverseSurface = Espresso20,
    inverseOnSurface = Espresso95,

    outline = Espresso60,
    outlineVariant = Espresso80,
    scrim = Espresso10,

    error = Red40,
    onError = Orange95,
    errorContainer = Red90,
    onErrorContainer = Red10,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Orange80,
    onPrimary = Orange20,
    primaryContainer = Orange30,
    onPrimaryContainer = Orange95,
    inversePrimary = Orange40,

    secondary = Brown80,
    onSecondary = Brown20,
    secondaryContainer = Brown30,
    onSecondaryContainer = Brown90,

    tertiary = Terracotta80,
    onTertiary = Terracotta20,
    tertiaryContainer = Terracotta30,
    onTertiaryContainer = Terracotta90,

    background = Espresso15,
    onBackground = Espresso95,

    surface = Espresso15,
    onSurface = Espresso95,
    surfaceVariant = Espresso30,
    onSurfaceVariant = Espresso80,
    surfaceTint = Orange80,
    inverseSurface = Espresso95,
    inverseOnSurface = Espresso20,

    outline = Espresso60,
    outlineVariant = Espresso30,
    scrim = Espresso10,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
)

// ---------------------------------------------------------------------------
// Typography — SansSerif throughout; editorial weight on display/headline.
// ---------------------------------------------------------------------------

private val RecipeTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    ),
)

// ---------------------------------------------------------------------------
// Shapes — stepped up from M3 defaults so heroes and cards feel confident.
// ---------------------------------------------------------------------------

private val RecipeShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------------------
// Hero gradients — three-stop vertical Brushes used by `GradientHero`.
// ---------------------------------------------------------------------------

private val RecipeHeroLightGradient: Brush = Brush.verticalGradient(
    colors = listOf(Orange95, Brown95, Espresso99),
)

private val RecipeHeroDarkGradient: Brush = Brush.verticalGradient(
    colors = listOf(Espresso30, Espresso20, Espresso15),
)

// ---------------------------------------------------------------------------
// Public DesignTokens implementation for the recipe app.
// ---------------------------------------------------------------------------

/**
 * Recipe Keeper's brand tokens. Placeholder palette until WS5b; the
 * object name and wiring are final.
 *
 * Pass this to [com.llmlocal.core.designsystem.theme.AppTheme] at the app
 * entry point (see `MainActivity.onCreate`).
 */
object RecipeKeeperTokens : DesignTokens {
    override val lightColorScheme: ColorScheme = LightColors
    override val darkColorScheme: ColorScheme = DarkColors
    override val typography: Typography = RecipeTypography
    override val shapes: Shapes = RecipeShapes
    override val heroLightGradient: Brush = RecipeHeroLightGradient
    override val heroDarkGradient: Brush = RecipeHeroDarkGradient
    override val successColor: Color = Sage40
}
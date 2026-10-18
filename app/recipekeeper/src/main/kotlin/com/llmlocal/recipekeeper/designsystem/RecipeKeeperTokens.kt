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
// Recipe Keeper brand palette (UX spec §8).
//
// Scrapbook / editorial cookbook voice. Avoids the orange of `:app:recipes`
// and the teal of `:app:medical`.
//  - Primary plum   (H≈335) — headlines, primary actions, hero scrim.
//  - Secondary olive (H≈95) — secondary containers, candidate badges, meta chips.
//  - Tertiary saffron (H≈45) — FAB badge, "Found" accent, incomplete chip.
//  - Neutrals parchment — warm off-whites and inky darks.
//  - Error          — M3 warm reds.
//  - successColor   — sage green (used by `StatusTone.Installed`).
//
// Typography uses `FontFamily.Serif` on display / headline / titleLarge so
// the app reads as a cookbook, not a productivity tool.
// ---------------------------------------------------------------------------

// Primary — plum (H≈335). Plum40 on Paper99 ≈ 7:1, Plum80 on Ink15 ≈ 9:1.
private val Plum10 = Color(0xFF3B0B26)
private val Plum20 = Color(0xFF52203C)
private val Plum30 = Color(0xFF662E4D)
private val Plum40 = Color(0xFF7A3B5E)
private val Plum80 = Color(0xFFF0B3D1)
private val Plum90 = Color(0xFFFFD8E7)
private val Plum95 = Color(0xFFFFECF3)

// Secondary — olive (H≈95).
private val Olive10 = Color(0xFF1A2409)
private val Olive20 = Color(0xFF2E3A17)
private val Olive30 = Color(0xFF445328)
private val Olive40 = Color(0xFF596B3A)
private val Olive80 = Color(0xFFBFD39A)
private val Olive90 = Color(0xFFDBEBB8)
private val Olive95 = Color(0xFFEBF5D2)

// Tertiary — saffron (H≈45).
private val Saffron10 = Color(0xFF2A1D00)
private val Saffron20 = Color(0xFF463300)
private val Saffron30 = Color(0xFF684F05)
private val Saffron40 = Color(0xFF8A6A12)
private val Saffron80 = Color(0xFFF2D27A)
private val Saffron90 = Color(0xFFFFE3A8)
private val Saffron95 = Color(0xFFFFEFC2)

// Neutrals — warm parchment / ink.
private val Paper95 = Color(0xFFF6EFE6)
private val Paper99 = Color(0xFFFFFBF7)
private val Ink10 = Color(0xFF1C1418)
private val Ink15 = Color(0xFF231A20)
private val Ink20 = Color(0xFF312229)
private val Ink30 = Color(0xFF48383F)
private val Ink40 = Color(0xFF604957)
private val Ink60 = Color(0xFF8B737F)
private val Ink80 = Color(0xFFC1AAB2)
private val Ink90 = Color(0xFFE5D2D8)

// Error — M3 warm reds.
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// Semantic — sage success.
private val Sage40 = Color(0xFF3E7A4E)

// ---------------------------------------------------------------------------
// Schemes — every M3 slot is filled so the app never falls back to the
// default M3 purple.
// ---------------------------------------------------------------------------

private val LightColors: ColorScheme = lightColorScheme(
    primary = Plum40,
    onPrimary = Plum95,
    primaryContainer = Plum90,
    onPrimaryContainer = Plum10,
    inversePrimary = Plum80,

    secondary = Olive40,
    onSecondary = Olive95,
    secondaryContainer = Olive90,
    onSecondaryContainer = Olive10,

    tertiary = Saffron40,
    onTertiary = Saffron95,
    tertiaryContainer = Saffron90,
    onTertiaryContainer = Saffron10,

    background = Paper99,
    onBackground = Ink10,

    surface = Paper99,
    onSurface = Ink10,
    surfaceVariant = Paper95,
    onSurfaceVariant = Ink40,
    surfaceTint = Plum40,
    inverseSurface = Ink20,
    inverseOnSurface = Paper95,

    outline = Ink60,
    outlineVariant = Ink80,
    scrim = Ink10,

    error = Red40,
    onError = Paper99,
    errorContainer = Red90,
    onErrorContainer = Red10,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Plum80,
    onPrimary = Plum20,
    primaryContainer = Plum30,
    onPrimaryContainer = Plum95,
    inversePrimary = Plum40,

    secondary = Olive80,
    onSecondary = Olive20,
    secondaryContainer = Olive30,
    onSecondaryContainer = Olive95,

    tertiary = Saffron80,
    onTertiary = Saffron20,
    tertiaryContainer = Saffron30,
    onTertiaryContainer = Saffron95,

    background = Ink15,
    onBackground = Paper95,

    surface = Ink15,
    onSurface = Paper95,
    surfaceVariant = Ink30,
    onSurfaceVariant = Ink80,
    surfaceTint = Plum80,
    inverseSurface = Paper95,
    inverseOnSurface = Ink20,

    outline = Ink60,
    outlineVariant = Ink30,
    scrim = Ink10,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
)

// ---------------------------------------------------------------------------
// Typography — serif on display/headline/titleLarge, sans for everything
// else. Body bumped to 17sp (bodyLarge) per UX spec §8.
// ---------------------------------------------------------------------------

private val RecipeKeeperTypography: Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 46.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
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
        fontSize = 17.sp,
        lineHeight = 26.sp,
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
// Shapes — one notch flatter than M3 defaults (4/8/12/16/24) per UX spec §8.
// ---------------------------------------------------------------------------

private val RecipeKeeperShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

// ---------------------------------------------------------------------------
// Hero gradients — three-stop vertical brushes (UX spec §8).
// ---------------------------------------------------------------------------

private val RecipeKeeperHeroLight: Brush = Brush.verticalGradient(
    colors = listOf(Paper95, Color(0xFFFFECF3), Paper99),
)

private val RecipeKeeperHeroDark: Brush = Brush.verticalGradient(
    colors = listOf(Color(0xFF5A2744), Ink20, Ink15),
)

/**
 * Recipe Keeper's brand tokens — plum / olive / saffron / parchment palette,
 * serif display type, flatter shapes, editorial gradients.
 *
 * Pass this to [com.llmlocal.core.designsystem.theme.AppTheme] at the app
 * entry point (see `MainActivity.onCreate`).
 */
object RecipeKeeperTokens : DesignTokens {
    override val lightColorScheme: ColorScheme = LightColors
    override val darkColorScheme: ColorScheme = DarkColors
    override val typography: Typography = RecipeKeeperTypography
    override val shapes: Shapes = RecipeKeeperShapes
    override val heroLightGradient: Brush = RecipeKeeperHeroLight
    override val heroDarkGradient: Brush = RecipeKeeperHeroDark
    override val successColor: Color = Sage40
}
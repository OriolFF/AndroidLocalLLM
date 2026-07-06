package com.llmlocal.medical.designsystem

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
// Brand palette — calming teal / clinical blue / sage / cool neutrals.
//
// The recipe app's brand is warm orange + caramel; the medical app uses
// teal + blue to evoke "calm, trustworthy, clinical" without leaning on
// the kind of saturated red that signals urgency. Every M3 slot is filled
// so the surface still feels intentional instead of falling back to M3
// defaults.
// ---------------------------------------------------------------------------

// Primary — teal (the brand accent).
private val Teal10 = Color(0xFF002722)
private val Teal20 = Color(0xFF003D38)
private val Teal30 = Color(0xFF00564E)
private val Teal40 = Color(0xFF0F766E) // brand "Primary" — light scheme
private val Teal50 = Color(0xFF14918A)
private val Teal60 = Color(0xFF3FAFA6)
private val Teal70 = Color(0xFF5DC2BB)
private val Teal80 = Color(0xFF80D6D0) // dark scheme
private val Teal90 = Color(0xFFA6EBE6)
private val Teal95 = Color(0xFFCFF6F3)

// Secondary — clinical blue (used for secondary accents).
private val Blue10 = Color(0xFF001A41)
private val Blue20 = Color(0xFF002D69)
private val Blue30 = Color(0xFF004494)
private val Blue40 = Color(0xFF1E40AF) // light secondary
private val Blue50 = Color(0xFF2E50C0)
private val Blue60 = Color(0xFF4364D2)
private val Blue70 = Color(0xFF5C7BE4)
private val Blue80 = Color(0xFF7E97F6)
private val Blue90 = Color(0xFFB6C5FB)
private val Blue95 = Color(0xFFDEE5FD)

// Tertiary — sage (used for "Self-Care" tags + warm-but-soft accents).
private val Sage10 = Color(0xFF0E2A1A)
private val Sage20 = Color(0xFF1A4128)
private val Sage30 = Color(0xFF2A5A39)
private val Sage40 = Color(0xFF3A7249)
private val Sage50 = Color(0xFF4C8A5A)
private val Sage60 = Color(0xFF62A26E)
private val Sage70 = Color(0xFF7DBA86)
private val Sage80 = Color(0xFF9CD3A4)
private val Sage90 = Color(0xFFBFE6C5)
private val Sage95 = Color(0xFFE0F3E3)

// Neutral — cool slate (cooler than recipe's espresso; signals "clinical").
private val Slate10 = Color(0xFF0A1014)
private val Slate15 = Color(0xFF121820)
private val Slate20 = Color(0xFF1B232C)
private val Slate30 = Color(0xFF2C3640)
private val Slate40 = Color(0xFF3F4954)
private val Slate50 = Color(0xFF535E6A)
private val Slate60 = Color(0xFF6A7682)
private val Slate70 = Color(0xFF838E99)
private val Slate80 = Color(0xFFA0AAB4)
private val Slate90 = Color(0xFFC1C9D1)
private val Slate95 = Color(0xFFE2E7EC)
private val Slate99 = Color(0xFFF7F9FB)

// Error — slightly desaturated red so it pairs with the teal palette.
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

// Semantic — bright teal for the "Installed" dot.
private val InstalledTeal = Color(0xFF14B8A6)

// ---------------------------------------------------------------------------
// Schemes — every M3 slot is filled.
// ---------------------------------------------------------------------------

private val LightColors: ColorScheme = lightColorScheme(
    primary = Teal40,
    onPrimary = Teal95,
    primaryContainer = Teal90,
    onPrimaryContainer = Teal10,
    inversePrimary = Teal80,

    secondary = Blue40,
    onSecondary = Blue95,
    secondaryContainer = Blue90,
    onSecondaryContainer = Blue10,

    tertiary = Sage40,
    onTertiary = Sage95,
    tertiaryContainer = Sage90,
    onTertiaryContainer = Sage10,

    background = Slate99,
    onBackground = Slate10,

    surface = Slate99,
    onSurface = Slate10,
    surfaceVariant = Slate95,
    onSurfaceVariant = Slate40,
    surfaceTint = Teal40,
    inverseSurface = Slate20,
    inverseOnSurface = Slate95,

    outline = Slate60,
    outlineVariant = Slate80,
    scrim = Slate10,

    error = Red40,
    onError = Teal95,
    errorContainer = Red90,
    onErrorContainer = Red10,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal20,
    primaryContainer = Teal30,
    onPrimaryContainer = Teal95,
    inversePrimary = Teal40,

    secondary = Blue80,
    onSecondary = Blue20,
    secondaryContainer = Blue30,
    onSecondaryContainer = Blue90,

    tertiary = Sage80,
    onTertiary = Sage20,
    tertiaryContainer = Sage30,
    onTertiaryContainer = Sage90,

    background = Slate15,
    onBackground = Slate95,

    surface = Slate15,
    onSurface = Slate95,
    surfaceVariant = Slate30,
    onSurfaceVariant = Slate80,
    surfaceTint = Teal80,
    inverseSurface = Slate95,
    inverseOnSurface = Slate20,

    outline = Slate60,
    outlineVariant = Slate30,
    scrim = Slate10,

    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
)

// ---------------------------------------------------------------------------
// Typography — SansSerif throughout (same shape as recipe; the medical
// app could ship a serif bundle later for a more "editorial" feel).
// ---------------------------------------------------------------------------

private val MedicalTypography: Typography = Typography(
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
// Shapes — same curve steps as recipe so cards feel familiar.
// ---------------------------------------------------------------------------

private val MedicalShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------------------
// Hero gradients — teal → blue → cool slate in light; slate stops in dark.
// ---------------------------------------------------------------------------

private val MedicalHeroLightGradient: Brush = Brush.verticalGradient(
    colors = listOf(Teal95, Blue95, Slate99),
)

private val MedicalHeroDarkGradient: Brush = Brush.verticalGradient(
    colors = listOf(Slate30, Slate20, Slate15),
)

// ---------------------------------------------------------------------------
// Public DesignTokens implementation for the medical app.
// ---------------------------------------------------------------------------

/**
 * The medical app's brand tokens — teal primary, clinical blue secondary,
 * sage tertiary for self-care tags, slate surfaces, and a bright-teal
 * "Installed" success color.
 */
object MedicalTokens : DesignTokens {
    override val lightColorScheme: ColorScheme = LightColors
    override val darkColorScheme: ColorScheme = DarkColors
    override val typography: Typography = MedicalTypography
    override val shapes: Shapes = MedicalShapes
    override val heroLightGradient: Brush = MedicalHeroLightGradient
    override val heroDarkGradient: Brush = MedicalHeroDarkGradient
    override val successColor: Color = InstalledTeal
}
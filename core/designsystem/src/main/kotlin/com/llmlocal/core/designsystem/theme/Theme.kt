package com.llmlocal.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Brand-first light scheme.
 *
 * Every Material 3 slot is filled so the app doesn't fall back to the
 * `Color(0xFF6650a4)` M3 defaults (which look out of place against a warm
 * orange palette). The scheme is the single source of truth for "what does
 * the app look like" when [dynamicColor] is disabled.
 */
private val LightColors = lightColorScheme(
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

/**
 * Brand-first dark scheme. Surfaces are warm-tinted (`Espresso15`, not pure
 * black) so cards don't feel like they're floating on a black void.
 */
private val DarkColors = darkColorScheme(
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

/**
 * App-wide Material 3 theme.
 *
 * Defaults to the brand palette (`dynamicColor = false`) so the warm orange
 * identity isn't overridden by Android 12+ wallpaper-derived colors. Screens
 * or previews that want system-driven hues can opt in by passing
 * `dynamicColor = true`.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

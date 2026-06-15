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

private val LightColors = lightColorScheme(
    primary = Orange40,
    onPrimary = Orange95,
    primaryContainer = Orange80,
    onPrimaryContainer = Orange20,
    secondary = Brown40,
    onSecondary = Orange95,
    secondaryContainer = Brown80,
    onSecondaryContainer = Brown20,
    background = Orange95,
    onBackground = Orange10,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Orange10,
)

private val DarkColors = darkColorScheme(
    primary = Orange80,
    onPrimary = Orange20,
    primaryContainer = Orange40,
    onPrimaryContainer = Orange95,
    secondary = Brown80Dark,
    onSecondary = Brown20,
    secondaryContainer = Brown40,
    onSecondaryContainer = Brown80Dark,
    background = Orange10,
    onBackground = Orange95,
    surface = Orange15,
    onSurface = Orange95,
)

/**
 * App-wide Material 3 theme.
 *
 * Dynamic color is enabled by default on Android 12+; callers can disable
 * it (e.g. for a brand-strict screen) via [dynamicColor].
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        content = content,
    )
}

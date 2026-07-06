package com.llmlocal.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * App-wide Material 3 theme.
 *
 * The caller supplies [tokens] — the design system itself owns no brand.
 * Each white-label `:app:*` module implements [DesignTokens] and passes its
 * object here (see `:app:recipes/.../designsystem/RecipeTokens` for the
 * warm-orange reference implementation).
 *
 * When [dynamicColor] is `true` and the platform supports it (Android 12+),
 * MaterialTheme falls back to wallpaper-derived hues regardless of [tokens].
 */
@Composable
fun AppTheme(
    tokens: DesignTokens,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> tokens.darkColorScheme
        else -> tokens.lightColorScheme
    }

    CompositionLocalProvider(LocalDesignTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = tokens.typography,
            shapes = tokens.shapes,
            content = content,
        )
    }
}
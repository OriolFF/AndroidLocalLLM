package com.llmlocal.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The brand tokens every consuming app supplies.
 *
 * The shared design system has no opinion on what your colors are — each
 * white-label `:app:*` module implements this interface to provide its own
 * palette, typography, shapes, gradient brushes, and the "success" color
 * used by semantic components like [com.llmlocal.core.designsystem.component.StatusDot].
 *
 * Tokens are static (no runtime mutation). Treat the implementing object as
 * immutable; the Compose CompositionLocal propagates the same instance for
 * the lifetime of the activity.
 */
interface DesignTokens {
    /** Brand `lightColorScheme(...)` — primary, secondary, tertiary, surfaces, etc. */
    val lightColorScheme: ColorScheme

    /** Brand `darkColorScheme(...)`. */
    val darkColorScheme: ColorScheme

    /** Brand `Typography(...)`. */
    val typography: Typography

    /** Brand `Shapes(...)`. */
    val shapes: Shapes

    /** Hero card background brush used by `GradientHero` in light mode. */
    val heroLightGradient: Brush

    /** Hero card background brush used by `GradientHero` in dark mode. */
    val heroDarkGradient: Brush

    /**
     * Semantic "success" color — used by `StatusTone.Installed` and any other
     * component that needs a brand-specific confirmation tint that isn't an
     * M3 slot. Brands should pick a hue that contrasts well against
     * `lightColorScheme.onPrimary` (the foreground for this background).
     */
    val successColor: Color
}

/**
 * CompositionLocal propagated by [AppTheme]. Read inside shared components
 * that need brand-specific values that aren't M3 slots (hero gradients,
 * the success color).
 *
 * `staticCompositionLocalOf` — these values never change at runtime, so we
 * skip the recomposition tracking `compositionLocalOf` would do.
 */
val LocalDesignTokens = staticCompositionLocalOf<DesignTokens> {
    error(
        "DesignTokens not provided. Wrap your content in AppTheme(tokens = …)."
    )
}

/**
 * Convenience accessor — `@Composable @ReadOnlyComposable` so it can be
 * called from any context where `MaterialTheme.colorScheme` would also be
 * valid.
 */
val designTokens: DesignTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalDesignTokens.current
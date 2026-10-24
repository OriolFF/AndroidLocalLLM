package com.llmlocal.core.designsystem.component.backdrop

/**
 * How [AccentBackdrop] paints a picture's [com.llmlocal.core.designsystem.color.ImageAccent]
 * behind a screen. Every style is static: drawn once and only redrawn when
 * the accent or the theme changes, so none costs battery while on screen.
 */
enum class AccentBackdropStyle {
    /** Plain theme surface; the accent is ignored. */
    NONE,

    /** A flat wash of the picture's main colour (HCT tone 93 light / 18 dark). */
    TINT,

    /**
     * The picture itself, heavily blurred and dimmed under a flat scrim.
     * Blurred with `RenderEffect` on Android 12+; older devices get the
     * bilinear upscale of the 48 px thumbnail, which is already soft.
     */
    PHOTO,
}

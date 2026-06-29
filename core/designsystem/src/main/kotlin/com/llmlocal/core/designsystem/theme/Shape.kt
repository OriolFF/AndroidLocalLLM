package com.llmlocal.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * App-wide [Shapes] override.
 *
 * Material 3 defaults (`small=8dp, medium=12dp, large=16dp, extraLarge=28dp`)
 * are bland for a recipe / food app — the rounded rectangles feel small. We
 * step them up a notch so heroes, cards, and CTAs have a more confident
 * curve without losing the M3 idiom.
 *
 *  - **extraSmall = 6dp** — pills, status badges, tags.
 *  - **small      = 10dp** — chips, small inputs.
 *  - **medium     = 16dp** — standard cards (recipe card, model card).
 *  - **large      = 20dp** — buttons, large inputs, the ingredients card.
 *  - **extraLarge = 28dp** — hero surfaces, bottom sheets (top corners only).
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

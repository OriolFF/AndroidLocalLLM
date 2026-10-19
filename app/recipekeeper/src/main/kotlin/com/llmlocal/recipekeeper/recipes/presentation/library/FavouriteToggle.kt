package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels

/**
 * Heart toggle (UX spec §1.1). 48dp touch target; state is carried by the
 * glyph fill and by `stateDescription`, not by colour.
 *
 * @param scrim draw the glyph on a 36dp dark disc so it reads over photos
 *   (library tile); the detail top bar passes `false` and a [tint]
 */
@Composable
fun FavouriteToggle(
    checked: Boolean,
    labels: RecipeKeeperLibraryLabels,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scrim: Boolean = false,
    tint: Color = Color.White,
) {
    val stateText = if (checked) labels.favouriteStateOn else labels.favouriteStateOff
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.semantics { stateDescription = stateText },
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .then(if (scrim) Modifier.background(Color.Black.copy(alpha = 0.35f)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (checked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = labels.favourite,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

package com.llmlocal.feature.webclipper.presentation.images

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llmlocal.core.designsystem.component.NumberBadge
import com.llmlocal.feature.webclipper.domain.input.ClipImage
import com.llmlocal.feature.webclipper.presentation.WebClipperLabels

/**
 * One numbered picture of the review grid (UX spec §2.4): 3:4 card, number
 * badge top-start, remove ✕ top-end (48dp hit area), a `primary` outline when
 * selected. The card is one merged TalkBack node ("Picture 2 of 3", selected
 * state) carrying Move left / Move right / Remove as custom actions; the ✕ is
 * a separate node so sighted switch users get a real button too.
 */
@Composable
internal fun ImageTile(
    image: ClipImage,
    index: Int,
    count: Int,
    selected: Boolean,
    labels: WebClipperLabels,
    onSelect: () -> Unit,
    onMove: (to: Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val outline by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "tile-outline",
    )
    val description = labels.imageTileDescription(index + 1, count)
    val removeLabel = labels.removeImage(index + 1, count)

    Box(modifier = modifier.aspectRatio(3f / 4f)) {
        Card(
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(2.dp, outline),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxSize()
                .selectable(selected = selected, role = Role.Button, onClick = onSelect)
                .semantics {
                    contentDescription = description
                    if (selected) stateDescription = labels.imageSelected
                    customActions = buildList {
                        if (index > 0) {
                            add(CustomAccessibilityAction(labels.moveImageLeft) { onMove(index - 1); true })
                        }
                        if (index < count - 1) {
                            add(CustomAccessibilityAction(labels.moveImageRight) { onMove(index + 1); true })
                        }
                        add(CustomAccessibilityAction(removeLabel) { onRemove(); true })
                    }
                },
        ) {
            AsyncImage(
                model = image.file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        NumberBadge(
            n = index + 1,
            selected = selected,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(scheme.scrim.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = removeLabel,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

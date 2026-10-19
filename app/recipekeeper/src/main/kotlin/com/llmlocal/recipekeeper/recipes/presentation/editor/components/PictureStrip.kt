package com.llmlocal.recipekeeper.recipes.presentation.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.llmlocal.recipekeeper.recipes.domain.EditorPicture
import com.llmlocal.recipekeeper.recipes.presentation.editor.RecipeEditorLabels
import java.io.File

private val TileSize = 96.dp

/**
 * Horizontal strip of picture tiles (existing, new, pending imports) followed by the Add tile.
 * The first tile is the cover; a tap on any other opens "Use as cover".
 */
@Composable
internal fun PictureStrip(
    pictures: List<EditorPicture>,
    importing: Int,
    maxPictures: Int,
    labels: RecipeEditorLabels,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onSetCover: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(pictures, key = { _, picture -> picture.key }) { index, picture ->
            PictureTile(
                picture = picture,
                index = index,
                total = pictures.size,
                labels = labels,
                onRemove = { onRemove(picture.key) },
                onSetCover = { onSetCover(picture.key) },
            )
        }
        items(importing) { PendingPictureTile() }
        if (pictures.size + importing < maxPictures) {
            item(key = "add-picture") { AddPictureTile(label = labels.addPicture, onClick = onAdd) }
        }
    }
}

@Composable
private fun PictureTile(
    picture: EditorPicture,
    index: Int,
    total: Int,
    labels: RecipeEditorLabels,
    onRemove: () -> Unit,
    onSetCover: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isCover = index == 0
    Box(modifier = Modifier.size(TileSize)) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            onClick = { if (!isCover) menuOpen = true },
            enabled = !isCover,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(picture.path)).crossfade(true).build(),
                contentDescription = labels.pictureDescription(index + 1, total),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(labels.useAsCover) },
                onClick = {
                    menuOpen = false
                    onSetCover()
                },
            )
        }
        if (isCover) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    text = labels.coverBadge,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        // 48dp hit area around a 24dp glyph on a scrim disc, readable over any photo.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = labels.removePicture(index + 1, total),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Placeholder for a picture that is still being copied from the picker. */
@Composable
private fun PendingPictureTile() {
    Box(
        modifier = Modifier
            .size(TileSize)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .clearAndSetSemantics { },
    )
}

@Composable
private fun AddPictureTile(label: String, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .size(TileSize)
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

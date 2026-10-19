package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.llmlocal.core.designsystem.theme.designTokens
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels
import com.llmlocal.recipekeeper.recipes.domain.RecipeSummary
import java.io.File

/**
 * One recipe in the library grid: 4:3 hero (Coil from the local file, or
 * the brand hero gradient with a `Restaurant` glyph), title on two lines,
 * `host · time` meta, and the "Possibly incomplete" badge when the model
 * output was cut off.
 *
 * Tap opens the recipe; long-press opens a menu (Delete / Share / Open
 * original) anchored to the card. The card is one semantics node whose
 * description is `title, from host`, so TalkBack reads it as a single row
 * and announces the long-press action label.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeTile(
    summary: RecipeSummary,
    labels: RecipeKeeperLibraryLabels,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tileDescription = labels.recipeTileDescription(summary.title, summary.sourceHost)

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = labels.recipeMenuDescription,
                )
                .semantics(mergeDescendants = true) { contentDescription = tileDescription },
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TileHero(summary = summary, labels = labels)
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tileMeta(summary, labels),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(labels.openOriginal) },
                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                onClick = { menuOpen = false; onOpenOriginal() },
            )
            DropdownMenuItem(
                text = { Text(labels.shareRecipe) },
                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                onClick = { menuOpen = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text(labels.delete, color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

@Composable
private fun TileHero(summary: RecipeSummary, labels: RecipeKeeperLibraryLabels) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val picture = summary.heroPicture
        if (!picture.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(picture))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(designTokens.heroLightGradient),
            )
            Icon(
                imageVector = Icons.Outlined.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
            )
        }
        if (summary.truncated) {
            IncompleteBadge(
                text = labels.incompleteChip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}

/** Saffron "Possibly incomplete" pill — the tertiary slot per UX spec §8. */
@Composable
fun IncompleteBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** `host · 30 min`, omitting the time when unknown. */
private fun tileMeta(summary: RecipeSummary, labels: RecipeKeeperLibraryLabels): String {
    val time = summary.totalMinutes?.let { labels.minutesTemplate(it) }
    return listOfNotNull(summary.sourceHost.takeIf { it.isNotBlank() }, time).joinToString(labels.metaSeparator)
}

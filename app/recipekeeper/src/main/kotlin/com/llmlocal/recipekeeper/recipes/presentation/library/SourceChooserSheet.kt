package com.llmlocal.recipekeeper.recipes.presentation.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.llmlocal.feature.webclipper.domain.input.ImagesAvailability
import com.llmlocal.recipekeeper.recipes.domain.RecipeSource
import com.llmlocal.recipekeeper.recipes.presentation.RecipeKeeperLibraryLabels

/**
 * "Add a recipe" sheet (UX spec §1): one row per [RecipeSource].
 *
 *  - web and editor are always enabled;
 *  - images is absent when [imagesAvailability] is `Disabled`, otherwise its
 *    supporting text says what is missing (`NoModel`, `ModelLacksVision`);
 *  - voice is a visible, disabled row with a "Coming soon" state description.
 *
 * Stateless: a tap sends [LibraryIntent.ChooseSource]; the ViewModel closes
 * the sheet and routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceChooserSheet(
    imagesAvailability: ImagesAvailability,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = { onIntent(LibraryIntent.DismissSourceChooser) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        SourceChooserContent(imagesAvailability = imagesAvailability, labels = labels, onIntent = onIntent)
    }
}

/** The sheet's body, separate from the [ModalBottomSheet] so previews and UI tests can render it directly. */
@Composable
fun SourceChooserContent(
    imagesAvailability: ImagesAvailability,
    labels: RecipeKeeperLibraryLabels,
    onIntent: (LibraryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)) {
            Text(
                text = labels.sourceChooserTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = labels.sourceChooserSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SourceRow(
            icon = Icons.Outlined.Public,
            headline = labels.sourceWeb,
            supporting = labels.sourceWebHint,
            onClick = { onIntent(LibraryIntent.ChooseSource(RecipeSource.WEB)) },
        )
        if (imagesAvailability != ImagesAvailability.Disabled) {
            SourceRow(
                icon = Icons.Outlined.PhotoLibrary,
                headline = labels.sourceImages,
                supporting = when (imagesAvailability) {
                    ImagesAvailability.NoModel -> labels.imagesNeedsModel
                    ImagesAvailability.ModelLacksVision -> labels.imagesModelCantRead
                    else -> labels.sourceImagesHint
                },
                onClick = { onIntent(LibraryIntent.ChooseSource(RecipeSource.IMAGES)) },
            )
        }
        SourceRow(
            icon = Icons.Outlined.Mic,
            headline = labels.sourceVoice,
            supporting = labels.sourceVoiceHint,
            enabled = false,
            statusLabel = labels.comingSoon,
            onClick = {},
        )
        SourceRow(
            icon = Icons.Outlined.EditNote,
            headline = labels.sourceEditor,
            supporting = labels.sourceEditorHint,
            onClick = { onIntent(LibraryIntent.ChooseSource(RecipeSource.EDITOR)) },
        )
    }
}

/**
 * One merged, whole-row-clickable node (headline + supporting + status are
 * read together). A disabled row dims to 38 % but keeps its status label at
 * full contrast and exposes it as the state description, so TalkBack reads
 * "By voice, Dictate a recipe, dimmed, Coming soon".
 */
@Composable
private fun SourceRow(
    icon: ImageVector,
    headline: String,
    supporting: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    statusLabel: String? = null,
) {
    // Only the row's content dims; the status label stays at full contrast so the reason is legible.
    val dim = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)
    ListItem(
        headlineContent = { Text(headline, modifier = dim) },
        supportingContent = { Text(supporting, modifier = dim) },
        leadingContent = { Box(modifier = dim) { SourceIcon(icon) } },
        trailingContent = {
            when {
                statusLabel != null -> Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                enabled -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .heightIn(min = 72.dp)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                if (!enabled) {
                    disabled()
                    statusLabel?.let { stateDescription = it }
                }
            },
    )
}

@Composable
private fun SourceIcon(icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * "This model can't read images" (UX spec §1.3): names the model, offers
 * "Open models" and Cancel. Shown after the sheet has closed.
 */
@Composable
fun ImagesUnavailableDialog(
    modelName: String,
    labels: RecipeKeeperLibraryLabels,
    onOpenModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.imagesUnavailableTitle) },
        text = { Text(labels.imagesUnavailableMessage(modelName)) },
        confirmButton = { TextButton(onClick = onOpenModels) { Text(labels.openModels) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(labels.cancel) } },
    )
}

/** M3's disabled content alpha. */
private const val DISABLED_ALPHA = 0.38f

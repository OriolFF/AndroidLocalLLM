package com.llmlocal.feature.webclipper.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.llmlocal.core.designsystem.component.StatusDot
import com.llmlocal.core.designsystem.component.StatusTone
import com.llmlocal.feature.webclipper.CandidateSource
import com.llmlocal.feature.webclipper.ClipCandidate
import com.llmlocal.feature.webclipper.WebClipperLabels

/**
 * Candidate picker: a modal sheet listing what the scan found on the
 * page (UX spec §3). Opened when more than one candidate exists; a single
 * detection skips it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidateSheet(
    candidates: List<ClipCandidate>,
    host: String,
    labels: WebClipperLabels,
    onPick: (candidateId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CandidateSheetContent(
            candidates = candidates,
            host = host,
            labels = labels,
            onPick = onPick,
        )
    }
}

/** Sheet body, split out so previews can render it without the modal. */
@Composable
fun CandidateSheetContent(
    candidates: List<ClipCandidate>,
    host: String,
    labels: WebClipperLabels,
    onPick: (candidateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = labels.candidatesTitle,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = labels.candidatesSubtitle(candidates.size, host),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            items(candidates, key = { it.id }) { candidate ->
                CandidateRow(candidate = candidate, labels = labels, onClick = { onPick(candidate.id) })
            }
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: ClipCandidate,
    labels: WebClipperLabels,
    onClick: () -> Unit,
) {
    val meta = labels.candidateMetaTemplate(candidate.ingredientCount, candidate.totalMinutes)
    val supporting = meta.ifBlank { candidate.snippet?.take(SNIPPET_CHARS).orEmpty() }
    val structured = candidate.source == CandidateSource.STRUCTURED
    ListItem(
        headlineContent = {
            Text(
                text = candidate.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (supporting.isNotBlank()) {
            { Text(text = supporting, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = {
            val fallback = rememberVectorPainter(Icons.Outlined.Image)
            AsyncImage(
                model = candidate.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback,
                fallback = fallback,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        },
        trailingContent = {
            StatusDot(
                label = if (structured) labels.sourceStructured else labels.sourceText,
                icon = if (structured) Icons.Outlined.DataObject else Icons.AutoMirrored.Outlined.Article,
                tone = if (structured) StatusTone.Installed else StatusTone.Idle,
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    )
}

private const val SNIPPET_CHARS = 100

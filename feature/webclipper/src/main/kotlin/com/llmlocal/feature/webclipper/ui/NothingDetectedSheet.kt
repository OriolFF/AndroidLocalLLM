package com.llmlocal.feature.webclipper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.feature.webclipper.WebClipperLabels

/**
 * "No recipe found on this page" sheet with *Extract anyway* (whole
 * readable article) and *Keep browsing*.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NothingDetectedSheet(
    labels: WebClipperLabels,
    onExtractAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        NothingDetectedSheetContent(
            labels = labels,
            onExtractAnyway = onExtractAnyway,
            onKeepBrowsing = onDismiss,
        )
    }
}

/** Sheet body, split out so previews can render it without the modal. */
@Composable
fun NothingDetectedSheetContent(
    labels: WebClipperLabels,
    onExtractAnyway: () -> Unit,
    onKeepBrowsing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = labels.nothingDetectedTitle,
            message = labels.nothingDetectedMessage,
            actionLabel = labels.extractAnyway,
            onAction = onExtractAnyway,
        )
        TextButton(onClick = onKeepBrowsing) {
            Text(labels.keepBrowsing)
        }
    }
}

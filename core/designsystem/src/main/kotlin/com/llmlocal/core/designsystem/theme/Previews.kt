package com.llmlocal.core.designsystem.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.llmlocal.core.designsystem.component.EmptyState
import com.llmlocal.core.designsystem.component.GradientHero
import com.llmlocal.core.designsystem.component.StatusDot
import com.llmlocal.core.designsystem.component.StatusTone

// ---------------------------------------------------------------------------
// @Preview composables for the design-system components.
//
// Rendered in Android Studio's Preview pane. Each component gets a light +
// dark variant. These composables are intentionally tiny — they exist so
// designers can iterate on visual decisions without rebuilding the app.
// ---------------------------------------------------------------------------

@Preview(name = "GradientHero / Light", showBackground = true)
@Composable
private fun GradientHeroLightPreview() {
    AppTheme(darkTheme = false) {
        GradientHero(
            title = "What's in your kitchen?",
            subtitle = "Add a few ingredients and we'll cook up a recipe.",
            icon = Icons.Outlined.AutoAwesome,
            actionLabel = "Get started",
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "GradientHero / Dark", showBackground = true)
@Composable
private fun GradientHeroDarkPreview() {
    AppTheme(darkTheme = true) {
        GradientHero(
            title = "What's in your kitchen?",
            subtitle = "Add a few ingredients and we'll cook up a recipe.",
            icon = Icons.Outlined.AutoAwesome,
            actionLabel = "Get started",
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "EmptyState / Light", showBackground = true)
@Composable
private fun EmptyStateLightPreview() {
    AppTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EmptyState(
                icon = Icons.Outlined.Inbox,
                title = "Nothing here yet",
                message = "Add a model to start generating recipes on this device.",
                actionLabel = "Browse models",
                onAction = {},
            )
            EmptyState(
                icon = Icons.Outlined.ErrorOutline,
                title = "Couldn't load models",
                message = "Check your connection and try again.",
                actionLabel = "Retry",
                onAction = {},
            )
        }
    }
}

@Preview(name = "EmptyState / Dark", showBackground = true)
@Composable
private fun EmptyStateDarkPreview() {
    AppTheme(darkTheme = true) {
        EmptyState(
            icon = Icons.Outlined.Inbox,
            title = "Nothing here yet",
            message = "Add a model to start generating recipes on this device.",
            actionLabel = "Browse models",
            onAction = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "StatusDot / Light", showBackground = true)
@Composable
private fun StatusDotLightPreview() {
    AppTheme(darkTheme = false) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(
                label = "Active",
                icon = Icons.Filled.Star,
                tone = StatusTone.Active,
            )
            StatusDot(
                label = "Installed",
                icon = Icons.Filled.CheckCircle,
                tone = StatusTone.Installed,
            )
            StatusDot(
                label = "Downloading",
                icon = Icons.Outlined.Memory,
                tone = StatusTone.Downloading,
            )
            StatusDot(
                label = "Failed",
                icon = Icons.Outlined.ErrorOutline,
                tone = StatusTone.Failed,
            )
            StatusDot(
                label = "Idle",
                icon = Icons.Outlined.Inbox,
                tone = StatusTone.Idle,
            )
        }
    }
}

@Preview(name = "StatusDot / Dark", showBackground = true)
@Composable
private fun StatusDotDarkPreview() {
    AppTheme(darkTheme = true) {
        StatusDot(
            label = "Active",
            icon = Icons.Filled.Star,
            tone = StatusTone.Active,
            modifier = Modifier.padding(16.dp),
        )
    }
}
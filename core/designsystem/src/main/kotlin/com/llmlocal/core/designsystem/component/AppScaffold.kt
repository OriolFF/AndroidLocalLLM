package com.llmlocal.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * App-wide scaffold.
 *
 * Wraps a Material 3 [Scaffold] so feature screens can stay edge-to-edge
 * without managing window insets themselves. The top app bar:
 *
 *  - Defaults to `MaterialTheme.colorScheme.surface` (tonal, not full white)
 *    so the bar disappears into the background until content scrolls
 *    underneath, at which point the surface gains a tonal elevation.
 *  - Title is rendered with `titleLarge` weight + ellipsis on overflow so a
 *    long screen title doesn't push the actions off the bar.
 *  - Exposes the standard slots to the caller: [navigationIcon] (leading),
 *    [actions] (trailing), [snackbarHost].
 *
 * `Modifier.consumeWindowInsets(...)` is applied so child screens don't
 * re-handle the same insets the scaffold already absorbed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(
        state = rememberTopAppBarState(),
    )
    val colorScheme = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    scrolledContainerColor = colorScheme.surfaceColorAtElevation(3.dp),
                    titleContentColor = colorScheme.onSurface,
                    navigationIconContentColor = colorScheme.onSurface,
                    actionIconContentColor = colorScheme.onSurfaceVariant,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = snackbarHost,
        content = { padding ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .consumeWindowInsets(padding)
                    .consumeWindowInsets(WindowInsets.systemBars),
            ) {
                content(padding)
            }
        },
    )
}
